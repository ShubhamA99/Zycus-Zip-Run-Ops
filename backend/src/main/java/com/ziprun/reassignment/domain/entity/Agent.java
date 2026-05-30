package com.ziprun.reassignment.domain.entity;

import com.ziprun.reassignment.domain.enums.AgentStatus;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "agents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Builder.Default
    @Column(name = "active_order_count", nullable = false)
    private Integer activeOrderCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AgentStatus status;

    // Sprint 2 placeholders (nullable)
    private String currentZone;

    private Integer maxCapacity;
}
