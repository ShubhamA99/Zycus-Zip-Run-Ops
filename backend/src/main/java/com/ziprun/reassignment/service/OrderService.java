package com.ziprun.reassignment.service;

import com.ziprun.reassignment.domain.entity.Agent;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.enums.OrderStatus;
import com.ziprun.reassignment.dto.CreateOrderRequest;
import com.ziprun.reassignment.dto.OrderResponse;
import com.ziprun.reassignment.repository.AgentRepository;
import com.ziprun.reassignment.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final AgentRepository agentRepository;

    public List<OrderResponse> getAllOrders(OrderStatus status) {
        List<Order> orders;
        if (status != null) {
            orders = orderRepository.findByStatus(status);
        } else {
            orders = orderRepository.findAll();
        }
        return orders.stream()
                .map(OrderResponse::fromEntity)
                .toList();
    }

    public OrderResponse getOrderById(String id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        return OrderResponse.fromEntity(order);
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request) {
        if (orderRepository.existsById(request.getId())) {
            throw new IllegalArgumentException("Order already exists: " + request.getId());
        }

        Agent agent = null;
        OrderStatus status = OrderStatus.PENDING;

        if (request.getAssignedAgentId() != null && !request.getAssignedAgentId().isBlank()) {
            agent = agentRepository.findById(request.getAssignedAgentId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Agent not found: " + request.getAssignedAgentId()));
            status = OrderStatus.ASSIGNED;
        }

        Order order = Order.builder()
                .id(request.getId())
                .description(request.getDescription())
                .assignedAgent(agent)
                .status(status)
                .build();

        Order saved = orderRepository.save(order);

        if (agent != null) {
            agent.setActiveOrderCount(agent.getActiveOrderCount() + 1);
            agentRepository.save(agent);
        }

        return OrderResponse.fromEntity(saved);
    }

    public List<Order> getOrdersByAgent(Agent agent) {
        return orderRepository.findByAssignedAgent(agent);
    }

    public List<Order> getAssignedOrdersByAgent(Agent agent) {
        return orderRepository.findByAssignedAgentAndStatus(agent, OrderStatus.ASSIGNED);
    }
}
