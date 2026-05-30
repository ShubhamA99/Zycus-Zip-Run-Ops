package com.ziprun.reassignment.dto.stream;

public record SuggestingEventData(
        String agentId,
        String agentName,
        Double confidence
) {}
