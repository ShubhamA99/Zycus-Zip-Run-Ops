package com.ziprun.reassignment.dto.stream;

public record ErrorEventData(
        String code,
        String message,
        String fallbackAction
) {}
