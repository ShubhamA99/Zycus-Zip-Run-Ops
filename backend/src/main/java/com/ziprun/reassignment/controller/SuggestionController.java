package com.ziprun.reassignment.controller;

import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import com.ziprun.reassignment.dto.SuggestionHistoryResponse;
import com.ziprun.reassignment.dto.SuggestionResponse;
import com.ziprun.reassignment.dto.UpdateSuggestionStatusRequest;
import com.ziprun.reassignment.dto.stream.*;
import com.ziprun.reassignment.service.StreamingSuggestionService;
import com.ziprun.reassignment.service.SuggestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/suggestions")
@RequiredArgsConstructor
public class SuggestionController {

    private final SuggestionService suggestionService;
    private final StreamingSuggestionService streamingService;

    @GetMapping
    public ResponseEntity<List<SuggestionResponse>> getAllSuggestions(
            @RequestParam(required = false) SuggestionStatus status) {
        return ResponseEntity.ok(suggestionService.getAllSuggestions(status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SuggestionResponse> getSuggestionById(@PathVariable Long id) {
        return ResponseEntity.ok(suggestionService.getSuggestionById(id));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<?> updateSuggestionStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateSuggestionStatusRequest request) {
        return ResponseEntity.ok(suggestionService.updateSuggestionStatus(id, request));
    }

    @GetMapping("/{id}/history")
    public ResponseEntity<SuggestionHistoryResponse> getSuggestionHistory(@PathVariable Long id) {
        return ResponseEntity.ok(suggestionService.getSuggestionHistory(id));
    }

    // ===== Streaming Endpoints =====

    /**
     * Start a new suggestion stream.
     * Returns immediately with suggestionId and URLs for streaming/reconnect.
     */
    @PostMapping("/start")
    public ResponseEntity<StartSuggestionResponse> startSuggestion(
            @RequestParam String orderId,
            @RequestParam(defaultValue = "MANUAL_REQUEST") TriggerReason reason) {
        return ResponseEntity.ok(streamingService.startSuggestion(orderId, reason));
    }

    /**
     * Subscribe to live event stream via SSE.
     * Only works for PROCESSING suggestions.
     */
    @GetMapping(value = "/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSuggestion(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(120_000L);
        streamingService.subscribeToStream(id, emitter);
        return emitter;
    }

    /**
     * Reconnect and fetch missed events.
     * Uses Redis if PROCESSING, DB if COMPLETED/FAILED.
     */
    @GetMapping("/{id}/reconnect")
    public ResponseEntity<ReconnectEventsResponse> reconnect(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int cursor) {
        return ResponseEntity.ok(streamingService.getEventsForReconnect(id, cursor));
    }

    /**
     * Get all events from DB (for COMPLETED/FAILED suggestions).
     */
    @GetMapping("/{id}/events")
    public ResponseEntity<List<StreamEventWrapper>> getEvents(@PathVariable Long id) {
        return ResponseEntity.ok(streamingService.getEventsFromDb(id));
    }

    /**
     * Get suggestion stream status.
     */
    @GetMapping("/{id}/status")
    public ResponseEntity<SuggestionStatusResponse> getStreamStatus(@PathVariable Long id) {
        return ResponseEntity.ok(streamingService.getStatus(id));
    }
}
