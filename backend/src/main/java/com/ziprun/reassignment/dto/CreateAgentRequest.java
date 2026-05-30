package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.enums.AgentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAgentRequest {

    @NotBlank(message = "Agent ID is required")
    private String id;

    @NotBlank(message = "Agent name is required")
    private String name;

    @NotNull(message = "Agent status is required")
    private AgentStatus status;
}
