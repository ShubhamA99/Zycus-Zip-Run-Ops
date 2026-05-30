package com.ziprun.reassignment.dto.stream;

public record SuggestionEventData(
        Long suggestionId,
        String agentId,
        String agentName,
        Double confidence,
        String reasoning,
        String triggerReason
) {}
