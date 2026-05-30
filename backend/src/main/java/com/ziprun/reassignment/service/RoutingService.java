package com.ziprun.reassignment.service;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.AgentStatus;
import com.ziprun.reassignment.domain.enums.OrderStatus;
import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.routing.RoutingStrategy;
import com.ziprun.reassignment.routing.RoutingStrategy.RecommendationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoutingService {

    private final Map<String, RoutingStrategy> strategies;
    private final ConfigService configService;
    private final AgentRepository agentRepository;
    private final OrderRepository orderRepository;
    private final SuggestionService suggestionService;

    /**
     * Get the currently active routing strategy from config
     */
    public RoutingStrategy getActiveStrategy() {
        String strategyName = configService.getRoutingStrategy();
        RoutingStrategy strategy = strategies.get(strategyName);

        if (strategy == null) {
            log.warn("Strategy '{}' not found, falling back to rule-based", strategyName);
            strategy = strategies.get("rule-based");
        }

        if (strategy == null) {
            throw new IllegalStateException("No routing strategy available");
        }

        return strategy;
    }

    /**
     * Generate a reassignment suggestion for an order
     */
    @Transactional
    public ReassignmentSuggestion generateSuggestion(String orderId, TriggerReason triggerReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

        // Get candidate agents (AVAILABLE + BUSY, excluding current agent)
        List<Agent> candidates = getCandidateAgents(order);

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No available agents for reassignment");
        }

        // Get recommendation from active strategy
        RoutingStrategy strategy = getActiveStrategy();
        log.info("Using strategy '{}' for order {}", strategy.getStrategyName(), orderId);

        // Use recommendWithContext to pass trigger reason (for AI prompts)
        RecommendationResult result = strategy.recommendWithContext(order, candidates, triggerReason);

        if (result == null) {
            throw new IllegalStateException("Strategy could not recommend an agent");
        }

        Agent recommendedAgent = result.agent();

        // Update order status to REASSIGNMENT_PENDING
        order.setStatus(OrderStatus.REASSIGNMENT_PENDING);
        orderRepository.save(order);

        // Create and save suggestion with confidence and reasoning from strategy
        ReassignmentSuggestion suggestion = ReassignmentSuggestion.builder()
                .order(order)
                .recommendedAgent(recommendedAgent)
                .status(SuggestionStatus.PENDING)
                .triggerReason(triggerReason)
                .confidence(result.confidence())
                .reasoning(result.reasoning())
                .build();

        ReassignmentSuggestion saved = suggestionService.saveSuggestion(suggestion);
        log.info("Created suggestion {} for order {} -> agent {} (confidence: {})",
                saved.getId(), orderId, recommendedAgent.getId(), result.confidence());

        return saved;
    }

    /**
     * Get list of candidate agents for reassignment
     */
    private List<Agent> getCandidateAgents(Order order) {
        List<Agent> candidates = new ArrayList<>();

        // Include AVAILABLE agents
        candidates.addAll(agentRepository.findByStatus(AgentStatus.AVAILABLE));

        // Include BUSY agents (they can take more orders)
        candidates.addAll(agentRepository.findByStatus(AgentStatus.BUSY));

        // Remove the current assigned agent from candidates
        if (order.getAssignedAgent() != null) {
            String currentAgentId = order.getAssignedAgent().getId();
            candidates.removeIf(a -> a.getId().equals(currentAgentId));
        }

        return candidates;
    }

    /**
     * List all available strategy names
     */
    public List<String> getAvailableStrategies() {
        return strategies.keySet().stream().sorted().toList();
    }
}
