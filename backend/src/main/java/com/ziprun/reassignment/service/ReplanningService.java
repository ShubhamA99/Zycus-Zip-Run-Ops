package com.ziprun.reassignment.service;

import com.ziprun.reassignment.domain.entity.*;
import com.ziprun.reassignment.domain.enums.*;
import com.ziprun.reassignment.event.ReplanResult;
import com.ziprun.reassignment.repository.*;
import com.ziprun.reassignment.routing.RoutingStrategy;
import com.ziprun.reassignment.routing.RoutingStrategy.RecommendationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplanningService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final FailedReplanRepository failedReplanRepository;
    private final ConfigService configService;
    private final Map<String, RoutingStrategy> strategies;

    public ReplanResult replanForAgent(String offlineAgentId) {
        log.info("Starting replan for offline agent: {}", offlineAgentId);

        List<Order> strandedOrders = findStrandedOrders(offlineAgentId);

        if (strandedOrders.isEmpty()) {
            log.info("No stranded orders found for agent: {}", offlineAgentId);
            return ReplanResult.noOrdersToReplan(offlineAgentId);
        }

        log.info("Found {} stranded orders for agent: {}", strandedOrders.size(), offlineAgentId);

        List<Agent> candidates = getCandidateAgents(offlineAgentId);

        if (candidates.isEmpty()) {
            log.warn("No candidate agents available for reassignment");
            return handleNoCandidates(offlineAgentId, strandedOrders);
        }

        List<Long> successIds = new ArrayList<>();
        List<Long> failedIds = new ArrayList<>();

        for (Order order : strandedOrders) {
            try {
                Long suggestionId = replanSingleOrder(order, candidates, offlineAgentId);
                if (suggestionId != null) {
                    successIds.add(suggestionId);
                }
            } catch (ReplanFailedException e) {
                failedIds.add(e.getFailedReplanId());
            }
        }

        return buildResult(offlineAgentId, strandedOrders.size(), successIds, failedIds);
    }

    private List<Order> findStrandedOrders(String agentId) {
        return orderRepository.findByAssignedAgent_IdAndStatusIn(
                agentId,
                List.of(OrderStatus.ASSIGNED, OrderStatus.REASSIGNMENT_PENDING)
        );
    }

    private List<Agent> getCandidateAgents(String excludeAgentId) {
        List<Agent> candidates = new ArrayList<>();
        candidates.addAll(agentRepository.findByStatus(AgentStatus.AVAILABLE));
        candidates.addAll(agentRepository.findByStatus(AgentStatus.BUSY));
        candidates.removeIf(a -> a.getId().equals(excludeAgentId));
        return candidates;
    }

    @Transactional
    public Long replanSingleOrder(Order order, List<Agent> candidates, String offlineAgentId)
            throws ReplanFailedException {

        if (hasPendingReplanSuggestion(order.getId())) {
            log.info("Skipping order {} - PENDING AGENT_OFFLINE suggestion already exists", order.getId());
            return null;
        }

        int maxAttempts = configService.getReplanMaxAttempts();
        int baseDelay = configService.getReplanBaseDelayMs();
        int maxJitter = configService.getReplanMaxJitterMs();

        Exception lastException = null;
        String activeStrategy = configService.getRoutingStrategy();

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                RecommendationResult result = callStrategy(activeStrategy, order, candidates);
                if (result != null) {
                    return saveSuggestion(order, result);
                }
            } catch (Exception e) {
                lastException = e;
                log.warn("Attempt {}/{} failed for order {}: {}",
                        attempt, maxAttempts, order.getId(), e.getMessage());

                if (attempt < maxAttempts) {
                    sleepWithJitter(baseDelay, maxJitter, attempt);
                }
            }
        }

        log.warn("All {} attempts failed for order {}", maxAttempts, order.getId());

        if (configService.isReplanFallbackEnabled() && !"rule-based".equals(activeStrategy)) {
            log.info("Attempting rule-based fallback for order {}", order.getId());
            try {
                RecommendationResult fallbackResult = callStrategy("rule-based", order, candidates);
                if (fallbackResult != null) {
                    String enhancedReasoning = "Fallback (AI unavailable): " + fallbackResult.reasoning();
                    RecommendationResult withFallbackNote = new RecommendationResult(
                            fallbackResult.agent(),
                            fallbackResult.confidence(),
                            enhancedReasoning
                    );
                    return saveSuggestion(order, withFallbackNote);
                }
            } catch (Exception e) {
                log.error("Fallback strategy also failed for order {}: {}", order.getId(), e.getMessage());
                lastException = e;
            }
        }

        Long failedId = saveFailedReplan(order, offlineAgentId, activeStrategy, maxAttempts, lastException);
        throw new ReplanFailedException(failedId);
    }

    private boolean hasPendingReplanSuggestion(String orderId) {
        return suggestionRepository.existsByOrder_IdAndStatusAndTriggerReason(
                orderId,
                SuggestionStatus.PENDING,
                TriggerReason.AGENT_OFFLINE
        );
    }

    private RecommendationResult callStrategy(String strategyName, Order order, List<Agent> candidates) {
        RoutingStrategy strategy = strategies.get(strategyName);
        if (strategy == null) {
            throw new IllegalStateException("Strategy not found: " + strategyName);
        }
        return strategy.recommendWithContext(order, candidates, TriggerReason.AGENT_OFFLINE);
    }

    private void sleepWithJitter(int baseDelay, int maxJitter, int attempt) {
        int delay = baseDelay * attempt;
        int jitter = ThreadLocalRandom.current().nextInt(0, maxJitter + 1);
        int totalDelay = delay + jitter;

        log.debug("Waiting {}ms before retry (base={}ms, jitter={}ms)", totalDelay, delay, jitter);

        try {
            Thread.sleep(totalDelay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Retry sleep interrupted", e);
        }
    }

    @Transactional
    protected Long saveSuggestion(Order order, RecommendationResult result) {
        order.setStatus(OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        ReassignmentSuggestion suggestion = ReassignmentSuggestion.builder()
                .order(order)
                .recommendedAgent(result.agent())
                .confidence(result.confidence())
                .reasoning(result.reasoning())
                .status(SuggestionStatus.PENDING)
                .triggerReason(TriggerReason.AGENT_OFFLINE)
                .build();

        ReassignmentSuggestion saved = suggestionRepository.save(suggestion);
        log.info("Created suggestion {} for order {} -> agent {} (confidence: {})",
                saved.getId(), order.getId(), result.agent().getId(), result.confidence());

        return saved.getId();
    }

    @Transactional
    protected Long saveFailedReplan(Order order, String offlineAgentId, String strategy,
                                     int attempts, Exception lastException) {
        FailedReplan failed = FailedReplan.builder()
                .order(order)
                .offlineAgentId(offlineAgentId)
                .strategyUsed(strategy)
                .attemptCount(attempts)
                .errorMessage(lastException != null ? lastException.getMessage() : "Unknown error")
                .status(FailedReplanStatus.PENDING_MANUAL_REVIEW)
                .build();

        FailedReplan saved = failedReplanRepository.save(failed);
        log.error("Saved failed replan {} for order {} - requires manual intervention",
                saved.getId(), order.getId());

        return saved.getId();
    }

    private ReplanResult handleNoCandidates(String agentId, List<Order> orders) {
        List<Long> failedIds = new ArrayList<>();
        for (Order order : orders) {
            Long failedId = saveFailedReplan(
                    order, agentId, "none", 0,
                    new IllegalStateException("No candidate agents available")
            );
            failedIds.add(failedId);
        }
        return ReplanResult.failed(agentId, orders.size(), failedIds);
    }

    private ReplanResult buildResult(String agentId, int total, List<Long> successIds, List<Long> failedIds) {
        if (failedIds.isEmpty()) {
            return ReplanResult.success(agentId, total, successIds);
        } else if (successIds.isEmpty()) {
            return ReplanResult.failed(agentId, total, failedIds);
        } else {
            return ReplanResult.partial(agentId, total, successIds.size(), successIds, failedIds);
        }
    }

    public static class ReplanFailedException extends Exception {
        private final Long failedReplanId;

        public ReplanFailedException(Long failedReplanId) {
            super("Replan failed, record ID: " + failedReplanId);
            this.failedReplanId = failedReplanId;
        }

        public Long getFailedReplanId() {
            return failedReplanId;
        }
    }
}
