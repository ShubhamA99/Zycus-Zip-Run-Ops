package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.enums.AgentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateAgentStatusRequest {

    @NotNull(message = "Status is required")
    private AgentStatus status;
}
