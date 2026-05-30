package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.dto.AgentResponse;
import com.ziprun.reassignment.dto.CreateAgentRequest;
import com.ziprun.reassignment.dto.UpdateAgentStatusRequest;
import com.ziprun.reassignment.service.AgentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/agents")
@RequiredArgsConstructor
public class AgentController {

    private final AgentService agentService;

    @GetMapping
    public ResponseEntity<List<AgentResponse>> getAllAgents() {
        return ResponseEntity.ok(agentService.getAllAgents());
    }

    @GetMapping("/{id}")
    public ResponseEntity<AgentResponse> getAgentById(@PathVariable String id) {
        return ResponseEntity.ok(agentService.getAgentById(id));
    }

    @PostMapping
    public ResponseEntity<AgentResponse> createAgent(@Valid @RequestBody CreateAgentRequest request) {
        AgentResponse created = agentService.createAgent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AgentResponse> updateAgentStatus(
            @PathVariable String id,
            @Valid @RequestBody UpdateAgentStatusRequest request) {
        return ResponseEntity.ok(agentService.updateAgentStatus(id, request));
    }
}
