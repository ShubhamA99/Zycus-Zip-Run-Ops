package com.ziprun.reassignment.routing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziprun.reassignment.ai.LLMGateway;
import com.ziprun.reassignment.ai.LLMResponse;
import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component("ai")
@RequiredArgsConstructor
@Slf4j
public class AIRoutingStrategy implements RoutingStrategy {

    private final LLMGateway llmGateway;
    private final RuleBasedRoutingStrategy fallbackStrategy;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<Agent> recommend(Order order, List<Agent> availableAgents) {
        // Default to MANUAL_REQUEST if no context provided
        var result = recommendWithContext(order, availableAgents, TriggerReason.MANUAL_REQUEST);
        return result != null ? Optional.of(result.agent()) : Optional.empty();
    }

    @Override
    public RecommendationResult recommendWithContext(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {

        if (availableAgents == null || availableAgents.isEmpty()) {
            log.warn("No available agents for AI recommendation");
            return null;
        }

        try {
            // Build prompt based on trigger reason
            String prompt = buildPrompt(order, availableAgents, triggerReason);
            log.debug("Sending prompt to LLM for order {}", order.getId());

            // Call LLM
            String response = llmGateway.callLLM(prompt);
            log.debug("LLM response for order {}: {}", order.getId(), response);

            // Parse JSON response
            LLMResponse parsed = parseResponse(response);

            // Validate agent ID exists in candidates
            Optional<Agent> matchedAgent = availableAgents.stream()
                    .filter(a -> a.getId().equals(parsed.agentId()))
                    .findFirst();

            if (matchedAgent.isEmpty()) {
                log.warn("LLM hallucinated agentId '{}' not in candidate list for order {}, falling back to rule-based",
                        parsed.agentId(), order.getId());
                return fallbackToRuleBased(order, availableAgents);
            }

            log.info("AI recommended agent {} with confidence {} for order {}",
                    parsed.agentId(), parsed.getConfidenceOrDefault(), order.getId());

            return new RecommendationResult(
                    matchedAgent.get(),
                    parsed.getConfidenceOrDefault(),
                    parsed.getReasoningOrDefault()
            );

        } catch (JsonProcessingException e) {
            log.warn("LLM returned malformed JSON for order {}: {}", order.getId(), e.getMessage());
            return fallbackToRuleBased(order, availableAgents);

        } catch (Exception e) {
            log.warn("LLM call failed for order {}: {}", order.getId(), e.getMessage());
            return fallbackToRuleBased(order, availableAgents);
        }
    }

    private String buildPrompt(Order order, List<Agent> agents, TriggerReason triggerReason) {
        String agentRoster = agents.stream()
                .map(a -> String.format("  - ID: %s, Name: %s, ActiveOrders: %d, Status: %s",
                        a.getId(), a.getName(), a.getActiveOrderCount(), a.getStatus()))
                .collect(Collectors.joining("\n"));

        if (triggerReason == TriggerReason.AGENT_OFFLINE) {
            return buildReplanPrompt(order, agentRoster);
        } else {
            return buildInitialPrompt(order, agentRoster);
        }
    }

    private String buildInitialPrompt(Order order, String agentRoster) {
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

                Respond with ONLY the JSON object, no additional text.
                """,
                order.getId(),
                order.getDescription(),
                order.getStatus(),
                agentRoster
        );
    }

    private String buildReplanPrompt(Order order, String agentRoster) {
        String previousAgent = order.getAssignedAgent() != null
                ? order.getAssignedAgent().getName() + " (" + order.getAssignedAgent().getId() + ")"
                : "Unknown";

        return String.format("""
                You are an intelligent delivery order reassignment system handling an URGENT recovery scenario.

                SITUATION: An agent has gone OFFLINE and their orders need immediate reassignment.

                FAILED AGENT: %s

                STRANDED ORDER REQUIRING REASSIGNMENT:
                - Order ID: %s
                - Description: %s
                - Original Status: %s
                - This order was in progress and is now stranded

                AVAILABLE AGENTS FOR REASSIGNMENT:
                %s

                RECOVERY PRIORITIES:
                1. URGENCY: This is a recovery scenario - prioritize speed of reassignment
                2. CAPACITY: Prefer agents with fewer active orders to handle the extra load
                3. RELIABILITY: Choose agents with AVAILABLE status over BUSY if possible
                4. BALANCE: Avoid overloading any single agent

                RESPONSE FORMAT (JSON only, no markdown):
                {"agentId":"<agent-id>","confidence":<0.0-1.0>,"reasoning":"<brief explanation of why this agent is best for recovery>"}

                Respond with ONLY the JSON object, no additional text.
                """,
                previousAgent,
                order.getId(),
                order.getDescription(),
                order.getStatus(),
                agentRoster
        );
    }

    private LLMResponse parseResponse(String raw) throws JsonProcessingException {
        // Strip markdown code blocks if LLM wraps response
        String json = raw
                .replaceAll("```json\\s*", "")
                .replaceAll("```\\s*", "")
                .trim();
        return objectMapper.readValue(json, LLMResponse.class);
    }

    private RecommendationResult fallbackToRuleBased(Order order, List<Agent> agents) {
        log.info("Falling back to rule-based strategy for order {}", order.getId());
        return fallbackStrategy.recommend(order, agents)
                .map(agent -> new RecommendationResult(
                        agent,
                        1.0,
                        "Fallback: Selected by rule-based strategy (lowest active order count)"
                ))
                .orElse(null);
    }

    @Override
    public String getStrategyName() {
        return "ai";
    }
}
