package com.ziprun.reassignment.routing;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component("rule-based")
public class RuleBasedRoutingStrategy implements RoutingStrategy {

    @Override
    public Optional<Agent> recommend(Order order, List<Agent> availableAgents) {
        if (availableAgents == null || availableAgents.isEmpty()) {
            return Optional.empty();
        }

        // Pick the agent with fewest active orders
        return availableAgents.stream()
                .min(Comparator.comparingInt(Agent::getActiveOrderCount));
    }

    @Override
    public RecommendationResult recommendWithContext(
            Order order,
            List<Agent> availableAgents,
            TriggerReason triggerReason) {
        return recommend(order, availableAgents)
                .map(agent -> new RecommendationResult(
                        agent,
                        1.0,  // Rule-based is deterministic
                        String.format(
                                "Selected %s based on lowest active order count (%d orders). " +
                                "Rule-based strategy prioritizes agents with the most available capacity.",
                                agent.getName(),
                                agent.getActiveOrderCount()
                        )
                ))
                .orElse(null);
    }

    @Override
    public String getStrategyName() {
        return "rule-based";
    }
}
