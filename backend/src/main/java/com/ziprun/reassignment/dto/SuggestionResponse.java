package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SuggestionResponse {

    private Long id;
    private String orderId;
    private String orderDescription;
    private String currentAgentId;
    private String currentAgentName;
    private String recommendedAgentId;
    private String recommendedAgentName;
    private Double confidence;
    private String reasoning;
    private SuggestionStatus status;
    private TriggerReason triggerReason;
    private LocalDateTime createdAt;

    public static SuggestionResponse fromEntity(ReassignmentSuggestion suggestion) {
        return SuggestionResponse.builder()
                .id(suggestion.getId())
                .orderId(suggestion.getOrder().getId())
                .orderDescription(suggestion.getOrder().getDescription())
                .currentAgentId(suggestion.getOrder().getAssignedAgent() != null
                        ? suggestion.getOrder().getAssignedAgent().getId() : null)
                .currentAgentName(suggestion.getOrder().getAssignedAgent() != null
                        ? suggestion.getOrder().getAssignedAgent().getName() : null)
                .recommendedAgentId(suggestion.getRecommendedAgent().getId())
                .recommendedAgentName(suggestion.getRecommendedAgent().getName())
                .confidence(suggestion.getConfidence())
                .reasoning(suggestion.getReasoning())
                .status(suggestion.getStatus())
                .triggerReason(suggestion.getTriggerReason())
                .createdAt(suggestion.getCreatedAt())
                .build();
    }
}
