package com.ziprun.reassignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.LLMResponse;
import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.*;
import com.ziprun.reassignment.dto.stream.*;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.ReassignmentSuggestionRepository;
import com.ziprun.reassignment.routing.RoutingStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionStreamWorker {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final SuggestionEventPublisher eventPublisher;
    private final ConfigService configService;
    private final LLMGateway llmGateway;
    private final Map<String, RoutingStrategy> strategies;
    private final Executor replanExecutor;
    private final ObjectMapper objectMapper;

    private static final int MAX_REPLAN_ATTEMPTS = 2;

    /**
     * Start the suggestion worker in background thread
     */
    public void startWorker(Long suggestionId, String orderId, TriggerReason triggerReason) {
        replanExecutor.execute(() -> {
            try {
                runSuggestionWorkflow(suggestionId, orderId, triggerReason);
            } catch (Exception e) {
                log.error("Worker failed for suggestion {}: {}", suggestionId, e.getMessage(), e);
                handleWorkerFailure(suggestionId, e.getMessage());
            }
        });
    }

    /**
     * Start retry worker with feedback context
     */
    public void startRetryWorker(
            Long suggestionId,
            String orderId,
            String rejectionFeedback,
            List<String> excludedAgentIds
    ) {
        replanExecutor.execute(() -> {
            try {
                runRetryWorkflow(suggestionId, orderId, rejectionFeedback, excludedAgentIds);
            } catch (Exception e) {
                log.error("Retry worker failed for suggestion {}: {}", suggestionId, e.getMessage(), e);
                handleWorkerFailure(suggestionId, e.getMessage());
            }
        });
    }

    private void runRetryWorkflow(
            Long suggestionId,
            String orderId,
            String rejectionFeedback,
            List<String> excludedAgentIds
    ) {
        AtomicInteger eventIndex = new AtomicInteger(0);
        List<StreamEventWrapper> allEvents = new ArrayList<>();

        BiConsumer<SuggestEventType, Object> emit = (type, data) -> {
            int idx = eventIndex.getAndIncrement();
            eventPublisher.publishEvent(suggestionId, idx, type, data);
            allEvents.add(StreamEventWrapper.of(idx, type, data));
        };

        emit.accept(SuggestEventType.STATUS, new StatusEventData(
                "Retrying with feedback: \"" + truncate(rejectionFeedback, 50) + "\""));

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            emit.accept(SuggestEventType.ERROR, new ErrorEventData(
                    "ORDER_NOT_FOUND", "Order not found: " + orderId, null));
            finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "Order not found");
            return;
        }

        List<Agent> allCandidates = getCandidateAgents(order.getAssignedAgent());
        List<Agent> candidates = allCandidates.stream()
                .filter(a -> !excludedAgentIds.contains(a.getId()))
                .toList();

        if (candidates.isEmpty()) {
            emit.accept(SuggestEventType.NO_CANDIDATES, new NoCandidatesEventData(
                    orderId, "No available agents after excluding " + excludedAgentIds.size() + " rejected"));
            finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "No candidates after exclusions");
            return;
        }

        emit.accept(SuggestEventType.STATUS, new StatusEventData(
                "Found " + candidates.size() + " candidates (excluded " + excludedAgentIds.size() + "). Analyzing..."));

        AIRecommendation recommendation = getRecommendationWithFeedback(
                order, candidates, rejectionFeedback, excludedAgentIds, emit);

        if (recommendation == null) {
            emit.accept(SuggestEventType.ERROR, new ErrorEventData(
                    "ROUTING_FAILED", "Could not determine recommendation", "MANUAL_ASSIGNMENT_REQUIRED"));
            finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "Routing failed");
            return;
        }

        emit.accept(SuggestEventType.SUGGESTING, new SuggestingEventData(
                recommendation.agent().getId(),
                recommendation.agent().getName(),
                recommendation.confidence()));

        sleep(500);

        Agent freshAgent = agentRepository.findById(recommendation.agent().getId()).orElse(null);

        if (freshAgent != null && freshAgent.getStatus() != AgentStatus.OFFLINE) {
            updateSuggestionWithResult(suggestionId, order, recommendation, TriggerReason.SUGGESTION_REJECTED);

            emit.accept(SuggestEventType.SUGGESTION, new SuggestionEventData(
                    suggestionId,
                    freshAgent.getId(),
                    freshAgent.getName(),
                    recommendation.confidence(),
                    recommendation.reasoning(),
                    "SUGGESTION_REJECTED"));

            finalizeStream(suggestionId, allEvents, StreamStatus.COMPLETED, null);
        } else {
            emit.accept(SuggestEventType.ERROR, new ErrorEventData(
                    "AGENT_UNAVAILABLE", "Recommended agent went offline", "MANUAL_ASSIGNMENT_REQUIRED"));
            finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "Agent unavailable");
        }
    }

    private AIRecommendation getRecommendationWithFeedback(
            Order order,
            List<Agent> candidates,
            String rejectionFeedback,
            List<String> excludedAgentIds,
            BiConsumer<SuggestEventType, Object> emit
    ) {
        String activeStrategy = configService.getRoutingStrategy();

        if ("ai".equals(activeStrategy)) {
            try {
                return callAIWithFeedback(order, candidates, rejectionFeedback, excludedAgentIds, emit);
            } catch (Exception e) {
                log.warn("AI streaming failed: {}, falling back to rule-based", e.getMessage());
                emit.accept(SuggestEventType.STATUS, new StatusEventData(
                        "AI unavailable, using rule-based fallback..."));
                return callRuleBasedFallback(order, candidates, TriggerReason.SUGGESTION_REJECTED);
            }
        } else {
            emit.accept(SuggestEventType.STATUS, new StatusEventData("Using rule-based strategy..."));
            return callRuleBasedFallback(order, candidates, TriggerReason.SUGGESTION_REJECTED);
        }
    }

    private AIRecommendation callAIWithFeedback(
            Order order,
            List<Agent> candidates,
            String rejectionFeedback,
            List<String> excludedAgentIds,
            BiConsumer<SuggestEventType, Object> emit
    ) {
        String prompt = buildPromptWithFeedback(order, candidates, rejectionFeedback, excludedAgentIds);
        emit.accept(SuggestEventType.STATUS, new StatusEventData("Calling AI with feedback context..."));

        StringBuilder fullResponse = new StringBuilder();

        llmGateway.callLLMStreaming(prompt, token -> {
            fullResponse.append(token);
            emit.accept(SuggestEventType.REASONING, new ReasoningEventData(token, false));
        });

        emit.accept(SuggestEventType.REASONING, new ReasoningEventData("", true));

        return parseAIResponse(fullResponse.toString(), candidates);
    }

    private String buildPromptWithFeedback(
            Order order,
            List<Agent> candidates,
            String rejectionFeedback,
            List<String> excludedAgentIds
    ) {
        String agentRoster = candidates.stream()
                .map(a -> String.format("  - ID: %s, Name: %s, ActiveOrders: %d, Status: %s",
                        a.getId(), a.getName(), a.getActiveOrderCount(), a.getStatus()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                You are an intelligent delivery order assignment system.

                TASK: Assign the following delivery order to the best available agent.

                IMPORTANT CONTEXT:
                A previous recommendation was REJECTED by operations.
                Rejection feedback: "%s"

                Do NOT recommend these previously rejected agents: %s
                Consider the feedback carefully when making your new recommendation.

                ORDER DETAILS:
                - Order ID: %s
                - Description: %s
                - Current Status: %s

                AVAILABLE AGENTS (excluding rejected):
                %s

                SELECTION CRITERIA:
                1. Address the rejection feedback
                2. Prefer agents with fewer active orders (better capacity)
                3. Consider agent availability status
                4. Balance workload across the team

                RESPONSE FORMAT (JSON only, no markdown):
                {"agentId":"<agent-id>","confidence":<0.0-1.0>,"reasoning":"<brief explanation addressing the feedback>"}
                """,
                rejectionFeedback,
                String.join(", ", excludedAgentIds),
                order.getId(),
                order.getDescription(),
                order.getStatus(),
                agentRoster);
    }

    private String truncate(String text, int maxLength) {
        if (text == null) return "";
        return text.length() > maxLength ? text.substring(0, maxLength) + "..." : text;
    }

    private void runSuggestionWorkflow(Long suggestionId, String orderId, TriggerReason triggerReason) {
        AtomicInteger eventIndex = new AtomicInteger(0);
        List<StreamEventWrapper> allEvents = new ArrayList<>();

        BiConsumer<SuggestEventType, Object> emit = (type, data) -> {
            int idx = eventIndex.getAndIncrement();
            eventPublisher.publishEvent(suggestionId, idx, type, data);
            allEvents.add(StreamEventWrapper.of(idx, type, data));
        };

        // 1. Loading order
        emit.accept(SuggestEventType.STATUS, new StatusEventData("Loading order details..."));

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            emit.accept(SuggestEventType.ERROR, new ErrorEventData(
                    "ORDER_NOT_FOUND", "Order not found: " + orderId, null));
            finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "Order not found");
            return;
        }

        // 2. Get candidates
        List<Agent> candidates = getCandidateAgents(order.getAssignedAgent());
        if (candidates.isEmpty()) {
            emit.accept(SuggestEventType.NO_CANDIDATES, new NoCandidatesEventData(
                    orderId, "No available agents for reassignment"));
            finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "No candidates");
            return;
        }

        emit.accept(SuggestEventType.STATUS, new StatusEventData(
                "Found " + candidates.size() + " candidate agents. Analyzing..."));

        // 3. Attempt loop
        int attempt = 0;
        List<String> excludedAgentIds = new ArrayList<>();

        while (attempt < MAX_REPLAN_ATTEMPTS) {
            attempt++;

            List<Agent> currentCandidates = candidates.stream()
                    .filter(a -> !excludedAgentIds.contains(a.getId()))
                    .toList();

            if (currentCandidates.isEmpty()) {
                emit.accept(SuggestEventType.NO_CANDIDATES, new NoCandidatesEventData(
                        orderId, "All candidate agents became unavailable"));
                finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "All candidates unavailable");
                return;
            }

            // 4. Get recommendation with streaming
            AIRecommendation recommendation = getRecommendationWithStreaming(
                    order, currentCandidates, triggerReason, emit);

            if (recommendation == null) {
                emit.accept(SuggestEventType.ERROR, new ErrorEventData(
                        "ROUTING_FAILED", "Could not determine recommendation", "MANUAL_ASSIGNMENT_REQUIRED"));
                finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "Routing failed");
                return;
            }

            // 5. Send suggesting (tentative)
            emit.accept(SuggestEventType.SUGGESTING, new SuggestingEventData(
                    recommendation.agent().getId(),
                    recommendation.agent().getName(),
                    recommendation.confidence()));

            sleep(500);

            // 6. Check agent availability
            Agent freshAgent = agentRepository.findById(recommendation.agent().getId()).orElse(null);

            if (freshAgent != null && freshAgent.getStatus() != AgentStatus.OFFLINE) {
                // Success - save and emit final suggestion
                updateSuggestionWithResult(suggestionId, order, recommendation, triggerReason);

                emit.accept(SuggestEventType.SUGGESTION, new SuggestionEventData(
                        suggestionId,
                        freshAgent.getId(),
                        freshAgent.getName(),
                        recommendation.confidence(),
                        recommendation.reasoning(),
                        triggerReason.name()));

                finalizeStream(suggestionId, allEvents, StreamStatus.COMPLETED, null);
                return;
            }

            // 7. Agent went offline - replan
            excludedAgentIds.add(recommendation.agent().getId());

            emit.accept(SuggestEventType.REPLANNING, new ReplanningEventData(
                    recommendation.agent().getId(),
                    recommendation.agent().getName(),
                    "Agent went OFFLINE",
                    attempt,
                    MAX_REPLAN_ATTEMPTS));

            if (attempt >= MAX_REPLAN_ATTEMPTS) {
                emit.accept(SuggestEventType.ERROR, new ErrorEventData(
                        "MAX_RETRIES_EXCEEDED",
                        "Recommended agents keep going offline. Max retries exceeded.",
                        "MANUAL_ASSIGNMENT_REQUIRED"));
                finalizeStream(suggestionId, allEvents, StreamStatus.FAILED, "Max retries exceeded");
                return;
            }

            emit.accept(SuggestEventType.STATUS, new StatusEventData("Finding alternative agent..."));
            sleep(300);
        }
    }

    private List<Agent> getCandidateAgents(Agent excludeAgent) {
        List<Agent> candidates = new ArrayList<>();
        candidates.addAll(agentRepository.findByStatus(AgentStatus.AVAILABLE));
        candidates.addAll(agentRepository.findByStatus(AgentStatus.BUSY));
        if (excludeAgent != null) {
            candidates.removeIf(a -> a.getId().equals(excludeAgent.getId()));
        }
        return candidates;
    }

    private AIRecommendation getRecommendationWithStreaming(
            Order order,
            List<Agent> candidates,
            TriggerReason triggerReason,
            BiConsumer<SuggestEventType, Object> emit
    ) {
        String activeStrategy = configService.getRoutingStrategy();

        if ("ai".equals(activeStrategy)) {
            try {
                return callAIWithStreaming(order, candidates, triggerReason, emit);
            } catch (Exception e) {
                log.warn("AI streaming failed: {}, falling back to rule-based", e.getMessage());
                emit.accept(SuggestEventType.STATUS, new StatusEventData(
                        "AI unavailable, using rule-based fallback..."));
                return callRuleBasedFallback(order, candidates, triggerReason);
            }
        } else {
            emit.accept(SuggestEventType.STATUS, new StatusEventData("Using rule-based strategy..."));
            return callRuleBasedFallback(order, candidates, triggerReason);
        }
    }

    private AIRecommendation callAIWithStreaming(
            Order order,
            List<Agent> candidates,
            TriggerReason triggerReason,
            BiConsumer<SuggestEventType, Object> emit
    ) {
        String prompt = buildPrompt(order, candidates, triggerReason);
        emit.accept(SuggestEventType.STATUS, new StatusEventData("Calling AI..."));

        StringBuilder fullResponse = new StringBuilder();

        llmGateway.callLLMStreaming(prompt, token -> {
            fullResponse.append(token);
            emit.accept(SuggestEventType.REASONING, new ReasoningEventData(token, false));
        });

        emit.accept(SuggestEventType.REASONING, new ReasoningEventData("", true));

        return parseAIResponse(fullResponse.toString(), candidates);
    }

    private AIRecommendation callRuleBasedFallback(Order order, List<Agent> candidates, TriggerReason triggerReason) {
        try {
            RoutingStrategy ruleBasedStrategy = strategies.get("rule-based");
            if (ruleBasedStrategy == null) {
                return null;
            }
            var result = ruleBasedStrategy.recommendWithContext(order, candidates, triggerReason);
            if (result == null) {
                return null;
            }
            return new AIRecommendation(result.agent(), result.confidence(), result.reasoning());
        } catch (Exception e) {
            log.error("Rule-based fallback failed: {}", e.getMessage());
            return null;
        }
    }

    private String buildPrompt(Order order, List<Agent> candidates, TriggerReason triggerReason) {
        String agentRoster = candidates.stream()
                .map(a -> String.format("  - ID: %s, Name: %s, ActiveOrders: %d, Status: %s",
                        a.getId(), a.getName(), a.getActiveOrderCount(), a.getStatus()))
                .collect(Collectors.joining("\n"));

        return String.format("""
                You are an intelligent delivery order assignment system.

                TASK: Assign the following delivery order to the best available agent.

                ORDER DETAILS:
                - Order ID: %s
                - Description: %s
                - Current Status: %s

                AVAILABLE AGENTS:
                %s

                SELECTION CRITERIA:
                1. Prefer agents with fewer active orders (better capacity)
                2. Consider agent availability status
                3. Balance workload across the team

                RESPONSE FORMAT (JSON only, no markdown):
                {"agentId":"<agent-id>","confidence":<0.0-1.0>,"reasoning":"<brief explanation>"}
                """,
                order.getId(), order.getDescription(), order.getStatus(), agentRoster);
    }

    private AIRecommendation parseAIResponse(String response, List<Agent> candidates) {
        try {
            String json = response.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
            int start = json.indexOf("{");
            int end = json.lastIndexOf("}") + 1;
            if (start >= 0 && end > start) {
                json = json.substring(start, end);
            }

            LLMResponse parsed = objectMapper.readValue(json, LLMResponse.class);
            Agent agent = candidates.stream()
                    .filter(a -> a.getId().equals(parsed.agentId()))
                    .findFirst()
                    .orElse(null);

            if (agent == null) {
                return null;
            }

            return new AIRecommendation(agent, parsed.getConfidenceOrDefault(), parsed.getReasoningOrDefault());
        } catch (Exception e) {
            log.error("Failed to parse AI response: {}", e.getMessage());
            return null;
        }
    }

    @Transactional
    public void updateSuggestionWithResult(Long suggestionId, Order order, AIRecommendation recommendation, TriggerReason triggerReason) {
        order.setStatus(OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId).orElseThrow();
        suggestion.setRecommendedAgent(recommendation.agent());
        suggestion.setConfidence(recommendation.confidence());
        suggestion.setReasoning(recommendation.reasoning());
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestionRepository.save(suggestion);

        log.info("Updated suggestion {} with agent {}", suggestionId, recommendation.agent().getId());
    }

    @Transactional
    public void finalizeStream(Long suggestionId, List<StreamEventWrapper> events, StreamStatus status, String errorMessage) {
        try {
            ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId).orElseThrow();
            suggestion.setStreamStatus(status);
            suggestion.setEventCount(events.size());
            suggestion.setEventsJson(objectMapper.writeValueAsString(events));
            suggestion.setStreamCompletedAt(LocalDateTime.now());
            if (errorMessage != null) {
                suggestion.setErrorMessage(errorMessage);
            }
            suggestionRepository.save(suggestion);

            eventPublisher.markStreamComplete(suggestionId);

            log.info("Finalized stream {} with status {} ({} events)", suggestionId, status, events.size());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize events for suggestion {}: {}", suggestionId, e.getMessage());
        }
    }

    private void handleWorkerFailure(Long suggestionId, String errorMessage) {
        try {
            ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId).orElse(null);
            if (suggestion != null) {
                suggestion.setStreamStatus(StreamStatus.FAILED);
                suggestion.setErrorMessage(errorMessage);
                suggestion.setStreamCompletedAt(LocalDateTime.now());
                suggestionRepository.save(suggestion);
            }
            eventPublisher.markStreamComplete(suggestionId);
        } catch (Exception e) {
            log.error("Failed to handle worker failure for {}: {}", suggestionId, e.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public record AIRecommendation(Agent agent, Double confidence, String reasoning) {}
}
