package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.OrderStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import com.ziprun.reassignment.dto.CreateOrderRequest;
import com.ziprun.reassignment.dto.OrderResponse;
import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.service.OrderService;
import com.ziprun.reassignment.service.RoutingService;
import com.ziprun.reassignment.service.StreamingSuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final RoutingService routingService;
    private final StreamingSuggestionService streamingSuggestionService;

    @GetMapping
    public ResponseEntity<List<OrderResponse>> getAllOrders(
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(orderService.getAllOrders(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponse> getOrderById(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        OrderResponse created = orderService.createOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PostMapping("/{id}/suggest")
    public ResponseEntity<SuggestionResponse> suggestReassignment(
            @PathVariable String id,
            @RequestParam(defaultValue = "MANUAL_REQUEST") TriggerReason reason) {
        ReassignmentSuggestion suggestion = routingService.generateSuggestion(id, reason);
        return ResponseEntity.ok(SuggestionResponse.fromEntity(suggestion));
    }

    /**
     * Stream suggestion with real-time updates via SSE.
     * Events: status, reasoning, suggesting, replanning, suggestion, error, no_candidates
     */
    @GetMapping(value = "/{id}/suggest/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSuggestion(
            @PathVariable String id,
            @RequestParam(defaultValue = "MANUAL_REQUEST") TriggerReason reason) {

        SseEmitter emitter = new SseEmitter(120_000L); // 2 minute timeout

        emitter.onCompletion(() -> {
            // Cleanup if needed
        });

        emitter.onTimeout(() -> {
            emitter.complete();
        });

        emitter.onError(e -> {
            emitter.completeWithError(e);
        });

        streamingSuggestionService.streamSuggestion(id, reason, emitter);

        return emitter;
    }
}
