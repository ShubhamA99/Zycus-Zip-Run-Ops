package com.ziprun.reassignment.event;

import java.time.LocalDateTime;

public record AgentOfflineEvent(
        String agentId,
        String agentName,
        LocalDateTime occurredAt
) {
    public AgentOfflineEvent(String agentId, String agentName) {
        this(agentId, agentName, LocalDateTime.now());
    }
}
