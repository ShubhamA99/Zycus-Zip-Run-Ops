package com.ziprun.reassignment.dto.stream;

public enum SuggestEventType {
    STATUS,        // General progress updates
    REASONING,     // AI thinking tokens (streamed)
    SUGGESTING,    // Tentative candidate (not yet verified)
    REPLANNING,    // Candidate unavailable, retrying
    SUGGESTION,    // Final confirmed suggestion
    ERROR,         // Failure occurred
    NO_CANDIDATES  // No agents available
}
