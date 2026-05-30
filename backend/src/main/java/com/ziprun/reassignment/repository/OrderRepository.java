package com.ziprun.reassignment.repository;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    List<Order> findByStatus(OrderStatus status);

    List<Order> findByAssignedAgent(Agent agent);

    List<Order> findByAssignedAgentAndStatus(Agent agent, OrderStatus status);

    List<Order> findByAssignedAgent_IdAndStatusIn(String agentId, List<OrderStatus> statuses);

    List<Order> findByAssignedAgent_Id(String agentId);
}
