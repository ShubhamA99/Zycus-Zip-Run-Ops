package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderResponse {

    private String id;
    private String description;
    private String assignedAgentId;
    private String assignedAgentName;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private String weightClass;
    private String pickupZone;
    private String dropoffZone;
    private LocalDateTime slaDeadline;

    public static OrderResponse fromEntity(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .description(order.getDescription())
                .assignedAgentId(order.getAssignedAgent() != null ? order.getAssignedAgent().getId() : null)
                .assignedAgentName(order.getAssignedAgent() != null ? order.getAssignedAgent().getName() : null)
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .weightClass(order.getWeightClass())
                .pickupZone(order.getPickupZone())
                .dropoffZone(order.getDropoffZone())
                .slaDeadline(order.getSlaDeadline())
                .build();
    }
}
