package com.ziprun.reassignment.dto.stream;

import com.ziprun.reassignment.domain.enums.StreamStatus;
import java.util.List;

public record ReconnectEventsResponse(
    Long suggestionId,
    StreamStatus streamStatus,
    Integer fromCursor,
    Integer toCursor,
    Integer totalEvents,
    List<StreamEventWrapper> events,
    boolean hasMore
) {}
