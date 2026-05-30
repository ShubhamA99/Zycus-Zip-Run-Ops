package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.enums.TriggerReason;

import java.util.List;
import java.util.Optional;

public interface RoutingStrategy {

    /**
     * Recommend an agent for the given order.
     *
     * @param order           The order needing assignment/reassignment
     * @param availableAgents List of agents eligible for assignment
     * @return Recommended agent, or empty if no suitable agent found
     */
    Optional<Agent> recommend(Order order, List<Agent> availableAgents);

    /**
     * Recommendation with trigger context.
     * AI strategy uses this to build different prompts for INITIAL vs AGENT_OFFLINE.
     * Default implementation ignores triggerReason (for backward compatibility).
     *
     * @param order           The order needing assignment/reassignment
     * @param availableAgents List of agents eligible for assignment
     * @param triggerReason   Why this recommendation is being requested
     * @return RecommendationResult with agent, confidence, and reasoning
     */
    default RecommendationResult recommendWithContext(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {
        return recommend(order, availableAgents)
                .map(agent -> new RecommendationResult(agent, 1.0,
                        "Selected by " + getStrategyName() + " strategy"))
                .orElse(null);
    }

    /**
     * @return Unique identifier for this strategy (matches bean name for Spring lookup)
     */
    String getStrategyName();

    /**
     * Result including confidence and reasoning from the routing strategy.
     */
    record RecommendationResult(
            Agent agent,
            double confidence,
            String reasoning
    ) {}
}
