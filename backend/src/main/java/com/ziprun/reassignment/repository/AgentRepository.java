package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.enums.AgentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AgentRepository extends JpaRepository<Agent, String> {

    List<Agent> findByStatus(AgentStatus status);

    List<Agent> findByStatusIn(List<AgentStatus> statuses);
}
