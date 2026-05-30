package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.entity.FailedReplan;
import com.ziprun.reassignment.domain.enums.FailedReplanStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FailedReplanRepository extends JpaRepository<FailedReplan, Long> {

    List<FailedReplan> findByStatus(FailedReplanStatus status);

    List<FailedReplan> findByOfflineAgentId(String offlineAgentId);

    List<FailedReplan> findByOrder_Id(String orderId);
}
