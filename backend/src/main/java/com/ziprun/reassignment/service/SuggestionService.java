package com.ziprun.reassignment.service;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.OrderStatus;
import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.dto.RejectionWithRetryResponse;
import com.ziprun.reassignment.dto.SuggestionChainItem;
import com.ziprun.reassignment.dto.SuggestionHistoryResponse;
import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.dto.UpdateSuggestionStatusRequest;
import com.ziprun.reassignment.dto.stream.StartSuggestionResponse;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.ReassignmentSuggestionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final ReassignmentSuggestionRepository suggestionRepository;
    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;
    private final StreamingSuggestionService streamingSuggestionService;

    public List<SuggestionResponse> getAllSuggestions(SuggestionStatus status) {
        List<ReassignmentSuggestion> suggestions;
        if (status != null) {
            suggestions = suggestionRepository.findByStatus(status);
        } else {
            suggestions = suggestionRepository.findAll();
        }
        return suggestions.stream()
                .map(SuggestionResponse::fromEntity)
                .toList();
    }

    public SuggestionResponse getSuggestionById(Long id) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found: " + id));
        return SuggestionResponse.fromEntity(suggestion);
    }

    @Transactional
    public Object updateSuggestionStatus(Long id, UpdateSuggestionStatusRequest request) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found: " + id));

        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new IllegalStateException(
                    "Cannot update suggestion that is not PENDING. Current status: " + suggestion.getStatus());
        }

        SuggestionStatus newStatus = request.getStatus();

        if (newStatus == SuggestionStatus.PENDING) {
            throw new IllegalArgumentException("Cannot set status back to PENDING");
        }

        if (newStatus == SuggestionStatus.ACCEPTED) {
            acceptSuggestion(suggestion);
        }

        if (newStatus == SuggestionStatus.REJECTED) {
            return handleRejection(suggestion, request.getFeedback());
        }

        suggestion.setStatus(newStatus);
        ReassignmentSuggestion saved = suggestionRepository.save(suggestion);

        return SuggestionResponse.fromEntity(saved);
    }

    private RejectionWithRetryResponse handleRejection(ReassignmentSuggestion suggestion, String feedback) {
        if (feedback == null || feedback.isBlank()) {
            throw new IllegalArgumentException("Feedback is required when rejecting a suggestion");
        }

        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setRejectionFeedback(feedback);
        suggestionRepository.save(suggestion);

        List<String> excludedAgentIds = collectExcludedAgentsFromChain(suggestion);

        StartSuggestionResponse newSuggestion = streamingSuggestionService.startRetrySuggestion(
                suggestion.getOrder().getId(),
                suggestion.getId(),
                feedback,
                excludedAgentIds
        );

        return new RejectionWithRetryResponse(
                SuggestionResponse.fromEntity(suggestion),
                newSuggestion
        );
    }

    private List<String> collectExcludedAgentsFromChain(ReassignmentSuggestion suggestion) {
        List<String> excludedIds = new ArrayList<>();

        ReassignmentSuggestion current = suggestion;
        while (current != null) {
            if (current.getRecommendedAgent() != null) {
                excludedIds.add(current.getRecommendedAgent().getId());
            }

            if (current.getParentSuggestionId() != null) {
                current = suggestionRepository.findById(current.getParentSuggestionId()).orElse(null);
            } else {
                current = null;
            }
        }

        return excludedIds;
    }

    private void acceptSuggestion(ReassignmentSuggestion suggestion) {
        Order order = suggestion.getOrder();
        Agent oldAgent = order.getAssignedAgent();
        Agent newAgent = suggestion.getRecommendedAgent();

        // Decrement old agent's order count (if exists and not same agent)
        if (oldAgent != null && !oldAgent.getId().equals(newAgent.getId())) {
            oldAgent.setActiveOrderCount(Math.max(0, oldAgent.getActiveOrderCount() - 1));
            agentRepository.save(oldAgent);
        }

        // Increment new agent's order count
        newAgent.setActiveOrderCount(newAgent.getActiveOrderCount() + 1);
        agentRepository.save(newAgent);

        // Update order assignment and status
        order.setAssignedAgent(newAgent);
        order.setStatus(OrderStatus.REASSIGNED);
        orderRepository.save(order);
    }

    public ReassignmentSuggestion saveSuggestion(ReassignmentSuggestion suggestion) {
        return suggestionRepository.save(suggestion);
    }

    public SuggestionHistoryResponse getSuggestionHistory(Long suggestionId) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestion not found: " + suggestionId));

        List<SuggestionChainItem> chain = new ArrayList<>();
        ReassignmentSuggestion current = suggestion;

        while (current != null) {
            chain.add(new SuggestionChainItem(
                    current.getId(),
                    current.getParentSuggestionId(),
                    current.getRecommendedAgent() != null ? current.getRecommendedAgent().getId() : null,
                    current.getRecommendedAgent() != null ? current.getRecommendedAgent().getName() : null,
                    current.getStatus(),
                    current.getRejectionFeedback(),
                    current.getCreatedAt()
            ));

            if (current.getParentSuggestionId() != null) {
                current = suggestionRepository.findById(current.getParentSuggestionId()).orElse(null);
            } else {
                current = null;
            }
        }

        return new SuggestionHistoryResponse(
                suggestionId,
                suggestion.getOrder().getId(),
                chain
        );
    }
}
