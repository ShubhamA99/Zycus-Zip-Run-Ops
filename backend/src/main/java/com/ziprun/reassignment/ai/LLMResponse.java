package com.ziprun.reassignment.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Expected LLM response format:
 * {"agentId":"AGT-002","confidence":0.85,"reasoning":"..."}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LLMResponse(
        String agentId,
        Double confidence,
        String reasoning
) {
    public double getConfidenceOrDefault() {
        return confidence != null ? confidence : 0.0;
    }

    public String getReasoningOrDefault() {
        return reasoning != null ? reasoning : "AI recommendation";
    }
}
