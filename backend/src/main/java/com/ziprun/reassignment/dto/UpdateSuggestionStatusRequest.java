package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSuggestionStatusRequest {

    @NotNull(message = "Status is required")
    private SuggestionStatus status;

    private String feedback;
}
