package com.ziprun.reassignment.dto;

import java.util.List;

public record SuggestionHistoryResponse(
    Long suggestionId,
    String orderId,
    List<SuggestionChainItem> chain
) {}
