package com.ziprun.reassignment.service;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.enums.AgentStatus;
import com.ziprun.reassignment.dto.AgentResponse;
import com.ziprun.reassignment.dto.CreateAgentRequest;
import com.ziprun.reassignment.dto.UpdateAgentStatusRequest;
import com.ziprun.reassignment.event.AgentOfflineEvent;
import com.ziprun.reassignment.repository.AgentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentService {

    private final AgentRepository agentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public List<AgentResponse> getAllAgents() {
        return agentRepository.findAll().stream()
                .map(AgentResponse::fromEntity)
                .toList();
    }

    public AgentResponse getAgentById(String id) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + id));
        return AgentResponse.fromEntity(agent);
    }

    @Transactional
    public AgentResponse createAgent(CreateAgentRequest request) {
        if (agentRepository.existsById(request.getId())) {
            throw new IllegalArgumentException("Agent already exists: " + request.getId());
        }

        Agent agent = Agent.builder()
                .id(request.getId())
                .name(request.getName())
                .status(request.getStatus())
                .activeOrderCount(0)
                .build();

        Agent saved = agentRepository.save(agent);
        return AgentResponse.fromEntity(saved);
    }

    @Transactional
    public AgentResponse updateAgentStatus(String id, UpdateAgentStatusRequest request) {
        Agent agent = agentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agent not found: " + id));

        AgentStatus oldStatus = agent.getStatus();
        AgentStatus newStatus = request.getStatus();

        agent.setStatus(newStatus);
        Agent saved = agentRepository.save(agent);

        if (oldStatus != AgentStatus.OFFLINE && newStatus == AgentStatus.OFFLINE) {
            log.info("Agent {} went OFFLINE, publishing event for replan", id);
            eventPublisher.publishEvent(new AgentOfflineEvent(saved.getId(), saved.getName()));
        }

        return AgentResponse.fromEntity(saved);
    }

    public List<AgentResponse> getAvailableAgents() {
        return agentRepository.findByStatus(AgentStatus.AVAILABLE).stream()
                .map(AgentResponse::fromEntity)
                .toList();
    }
}
