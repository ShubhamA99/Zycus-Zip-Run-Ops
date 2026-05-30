package com.ziprun.reassignment.dto.stream;

public record ReplanningEventData(
        String unavailableAgentId,
        String unavailableAgentName,
        String reason,
        int attemptNumber,
        int maxAttempts
) {}
