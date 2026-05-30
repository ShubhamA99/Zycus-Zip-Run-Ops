package com.ziprun.reassignment.dto;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.enums.AgentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentResponse {

    private String id;
    private String name;
    private Integer activeOrderCount;
    private AgentStatus status;
    private String currentZone;
    private Integer maxCapacity;

    public static AgentResponse fromEntity(Agent agent) {
        return AgentResponse.builder()
                .id(agent.getId())
                .name(agent.getName())
                .activeOrderCount(agent.getActiveOrderCount())
                .status(agent.getStatus())
                .currentZone(agent.getCurrentZone())
                .maxCapacity(agent.getMaxCapacity())
                .build();
    }
}
