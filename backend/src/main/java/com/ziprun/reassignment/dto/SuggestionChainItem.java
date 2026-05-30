package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import java.time.LocalDateTime;

public record SuggestionChainItem(
    Long suggestionId,
    Long parentSuggestionId,
    String recommendedAgentId,
    String recommendedAgentName,
    SuggestionStatus status,
    String rejectionFeedback,
    LocalDateTime createdAt
) {}
