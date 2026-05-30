package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReassignmentSuggestionRepository extends JpaRepository<ReassignmentSuggestion, Long> {

    List<ReassignmentSuggestion> findByStatus(SuggestionStatus status);

    List<ReassignmentSuggestion> findByOrderAndStatus(Order order, SuggestionStatus status);

    Optional<ReassignmentSuggestion> findByOrderAndStatusAndTriggerReason(
            Order order, SuggestionStatus status, TriggerReason triggerReason);

    boolean existsByOrder_IdAndStatusAndTriggerReason(
            String orderId, SuggestionStatus status, TriggerReason triggerReason);
}
