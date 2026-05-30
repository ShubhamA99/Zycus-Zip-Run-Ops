package com.ziprun.reassignment.dto.stream;

import com.ziprun.reassignment.domain.enums.StreamStatus;
import java.time.LocalDateTime;

public record SuggestionStatusResponse(
    Long suggestionId,
    StreamStatus streamStatus,
    Integer eventCount,
    LocalDateTime startedAt,
    LocalDateTime completedAt,
    String errorMessage
) {}
