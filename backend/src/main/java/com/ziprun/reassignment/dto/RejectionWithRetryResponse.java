package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.dto.stream.StartSuggestionResponse;

public record RejectionWithRetryResponse(
    SuggestionResponse rejectedSuggestion,
    StartSuggestionResponse newSuggestion
) {}
