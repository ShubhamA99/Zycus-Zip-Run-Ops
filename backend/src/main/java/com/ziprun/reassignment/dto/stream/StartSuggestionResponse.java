package com.ziprun.reassignment.dto.stream;

import com.ziprun.reassignment.domain.enums.StreamStatus;

public record StartSuggestionResponse(
    Long suggestionId,
    String orderId,
    StreamStatus streamStatus,
    String streamUrl,
    String reconnectUrl,
    String eventsUrl
) {}
