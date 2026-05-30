package com.ziprun.reassignment.dto.stream;

import java.time.Instant;

public record StreamEventWrapper(
    Integer index,
    String eventType,
    Object data,
    Instant timestamp
) {
    public static StreamEventWrapper of(int index, SuggestEventType type, Object data) {
        return new StreamEventWrapper(index, type.name().toLowerCase(), data, Instant.now());
    }
}
