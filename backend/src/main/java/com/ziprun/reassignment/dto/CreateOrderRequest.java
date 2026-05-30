package com.ziprun.reassignment.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotBlank(message = "Order ID is required")
    private String id;

    @NotBlank(message = "Description is required")
    private String description;

    private String assignedAgentId;
}
