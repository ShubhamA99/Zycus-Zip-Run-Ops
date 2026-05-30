package com.ziprun.reassignment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziprun.reassignment.domain.entity.Order;
import com.ziprun.reassignment.domain.entity.ReassignmentSuggestion;
import com.ziprun.reassignment.domain.enums.StreamStatus;
import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import com.ziprun.reassignment.dto.stream.*;
import com.ziprun.reassignment.repository.OrderRepository;
import com.ziprun.reassignment.repository.ReassignmentSuggestionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingSuggestionService {

    private final OrderRepository orderRepository;
    private final ReassignmentSuggestionRepository suggestionRepository;
    private final SuggestionStreamWorker streamWorker;
    private final SuggestionEventPublisher eventPublisher;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    /**
     * Start a new suggestion stream - returns immediately with suggestionId
     */
    @Transactional
    public StartSuggestionResponse startSuggestion(String orderId, TriggerReason triggerReason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        ReassignmentSuggestion suggestion = ReassignmentSuggestion.builder()
                .order(order)
                .status(SuggestionStatus.PENDING)
                .streamStatus(StreamStatus.PROCESSING)
                .triggerReason(triggerReason)
                .streamStartedAt(LocalDateTime.now())
                .eventCount(0)
                .build();

        ReassignmentSuggestion saved = suggestionRepository.save(suggestion);
        Long suggestionId = saved.getId();

        log.info("Created suggestion {} for order {}, starting worker", suggestionId, orderId);

        streamWorker.startWorker(suggestionId, orderId, triggerReason);

        return new StartSuggestionResponse(
                suggestionId,
                orderId,
                StreamStatus.PROCESSING,
                "/suggestions/" + suggestionId + "/stream",
                "/suggestions/" + suggestionId + "/reconnect",
                "/suggestions/" + suggestionId + "/events"
        );
    }

    /**
     * Start a retry suggestion after rejection - includes feedback context
     */
    @Transactional
    public StartSuggestionResponse startRetrySuggestion(
            String orderId,
            Long parentSuggestionId,
            String rejectionFeedback,
            List<String> excludedAgentIds
    ) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        ReassignmentSuggestion suggestion = ReassignmentSuggestion.builder()
                .order(order)
                .status(SuggestionStatus.PENDING)
                .streamStatus(StreamStatus.PROCESSING)
                .triggerReason(TriggerReason.SUGGESTION_REJECTED)
                .parentSuggestionId(parentSuggestionId)
                .streamStartedAt(LocalDateTime.now())
                .eventCount(0)
                .build();

        ReassignmentSuggestion saved = suggestionRepository.save(suggestion);
        Long suggestionId = saved.getId();

        log.info("Created retry suggestion {} for order {} (parent: {})",
                suggestionId, orderId, parentSuggestionId);

        streamWorker.startRetryWorker(suggestionId, orderId, rejectionFeedback, excludedAgentIds);

        return new StartSuggestionResponse(
                suggestionId,
                orderId,
                StreamStatus.PROCESSING,
                "/suggestions/" + suggestionId + "/stream",
                "/suggestions/" + suggestionId + "/reconnect",
                "/suggestions/" + suggestionId + "/events"
        );
    }

    /**
     * Subscribe to live stream via Redis pub/sub
     */
    public void subscribeToStream(Long suggestionId, SseEmitter emitter) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId).orElse(null);
        if (suggestion == null) {
            sendSseError(emitter, "Suggestion not found");
            return;
        }

        if (suggestion.getStreamStatus() != StreamStatus.PROCESSING) {
            sendSseError(emitter, "Stream already completed. Use /events endpoint.");
            return;
        }

        String channelKey = eventPublisher.getChannelKey(suggestionId);

        MessageListener listener = (message, pattern) -> {
            try {
                String json = new String(message.getBody());
                StreamEventWrapper event = objectMapper.readValue(json, StreamEventWrapper.class);

                emitter.send(SseEmitter.event()
                        .name(event.eventType())
                        .data(event.data()));

                if ("suggestion".equals(event.eventType()) ||
                    "error".equals(event.eventType()) ||
                    "no_candidates".equals(event.eventType())) {
                    emitter.complete();
                }
            } catch (IOException e) {
                log.warn("Failed to send SSE event: {}", e.getMessage());
            }
        };

        listenerContainer.addMessageListener(listener, new ChannelTopic(channelKey));

        emitter.onCompletion(() -> {
            listenerContainer.removeMessageListener(listener);
            log.debug("Removed Redis listener for suggestion {}", suggestionId);
        });

        emitter.onTimeout(() -> {
            listenerContainer.removeMessageListener(listener);
            emitter.complete();
        });

        emitter.onError(e -> {
            listenerContainer.removeMessageListener(listener);
        });

        log.info("Subscribed to stream for suggestion {}", suggestionId);
    }

    /**
     * Get events for reconnection (from Redis if PROCESSING, from DB if COMPLETED)
     */
    public ReconnectEventsResponse getEventsForReconnect(Long suggestionId, int cursor) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));

        if (suggestion.getStreamStatus() == StreamStatus.PROCESSING) {
            List<String> rawEvents = eventPublisher.getEventsFromHistory(suggestionId, cursor);
            List<StreamEventWrapper> events = rawEvents.stream()
                    .map(json -> {
                        try {
                            return objectMapper.readValue(json, StreamEventWrapper.class);
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(e -> e != null)
                    .toList();

            int totalEvents = eventPublisher.getEventCount(suggestionId);

            return new ReconnectEventsResponse(
                    suggestionId,
                    StreamStatus.PROCESSING,
                    cursor,
                    cursor + events.size(),
                    totalEvents,
                    events,
                    cursor + events.size() < totalEvents
            );
        } else {
            List<StreamEventWrapper> allEvents = getEventsFromDb(suggestion);
            List<StreamEventWrapper> events = allEvents.stream()
                    .skip(cursor)
                    .toList();

            return new ReconnectEventsResponse(
                    suggestionId,
                    suggestion.getStreamStatus(),
                    cursor,
                    allEvents.size(),
                    allEvents.size(),
                    events,
                    false
            );
        }
    }

    /**
     * Get all events from DB (for COMPLETED/FAILED suggestions)
     */
    public List<StreamEventWrapper> getEventsFromDb(Long suggestionId) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));
        return getEventsFromDb(suggestion);
    }

    private List<StreamEventWrapper> getEventsFromDb(ReassignmentSuggestion suggestion) {
        if (suggestion.getEventsJson() == null) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    suggestion.getEventsJson(),
                    new TypeReference<List<StreamEventWrapper>>() {}
            );
        } catch (Exception e) {
            log.error("Failed to parse events JSON: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Get suggestion status
     */
    public SuggestionStatusResponse getStatus(Long suggestionId) {
        ReassignmentSuggestion suggestion = suggestionRepository.findById(suggestionId)
                .orElseThrow(() -> new IllegalArgumentException("Suggestion not found"));

        return new SuggestionStatusResponse(
                suggestionId,
                suggestion.getStreamStatus(),
                suggestion.getEventCount(),
                suggestion.getStreamStartedAt(),
                suggestion.getStreamCompletedAt(),
                suggestion.getErrorMessage()
        );
    }

    // ===== Legacy method for backward compatibility =====

    /**
     * @deprecated Use startSuggestion() + subscribeToStream() instead
     */
    @Deprecated
    public void streamSuggestion(String orderId, TriggerReason triggerReason, SseEmitter emitter) {
        try {
            StartSuggestionResponse response = startSuggestion(orderId, triggerReason);
            subscribeToStream(response.suggestionId(), emitter);
        } catch (Exception e) {
            log.error("Legacy stream suggestion failed: {}", e.getMessage());
            sendSseError(emitter, e.getMessage());
        }
    }

    private void sendSseError(SseEmitter emitter, String message) {
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(new ErrorEventData("INVALID_STATE", message, null)));
            emitter.complete();
        } catch (IOException e) {
            log.warn("Failed to send SSE error: {}", e.getMessage());
        }
    }
}
