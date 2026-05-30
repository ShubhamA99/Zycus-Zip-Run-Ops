package com.ziprun.reassignment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ziprun.reassignment.dto.stream.StreamEventWrapper;
import com.ziprun.reassignment.dto.stream.SuggestEventType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SuggestionEventPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${suggestion.stream.redis-ttl-minutes:30}")
    private int redisTtlMinutes;

    private static final String CHANNEL_PREFIX = "suggestion:";
    private static final String CHANNEL_SUFFIX = ":events";
    private static final String LIST_PREFIX = "suggestion:";
    private static final String LIST_SUFFIX = ":history";

    public String getChannelKey(Long suggestionId) {
        return CHANNEL_PREFIX + suggestionId + CHANNEL_SUFFIX;
    }

    public String getListKey(Long suggestionId) {
        return LIST_PREFIX + suggestionId + LIST_SUFFIX;
    }

    /**
     * Publish event to both channel (live) and list (history buffer)
     */
    public void publishEvent(Long suggestionId, int index, SuggestEventType type, Object data) {
        StreamEventWrapper wrapper = StreamEventWrapper.of(index, type, data);
        String channelKey = getChannelKey(suggestionId);
        String listKey = getListKey(suggestionId);

        try {
            String json = objectMapper.writeValueAsString(wrapper);

            // Publish to channel (live subscribers)
            redisTemplate.convertAndSend(channelKey, json);

            // Push to list (reconnect buffer)
            redisTemplate.opsForList().rightPush(listKey, json);

            log.debug("Published event {} #{} to suggestion {}", type, index, suggestionId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event: {}", e.getMessage());
        }
    }

    /**
     * Mark stream complete and set TTL on Redis keys
     */
    public void markStreamComplete(Long suggestionId) {
        String listKey = getListKey(suggestionId);
        redisTemplate.expire(listKey, Duration.ofMinutes(redisTtlMinutes));
        log.info("Stream {} completed, Redis TTL set to {} minutes", suggestionId, redisTtlMinutes);
    }

    /**
     * Get events from history list (for reconnect)
     */
    public List<String> getEventsFromHistory(Long suggestionId, int fromCursor) {
        String listKey = getListKey(suggestionId);
        Long size = redisTemplate.opsForList().size(listKey);
        if (size == null || size == 0) {
            return List.of();
        }
        List<Object> rawEvents = redisTemplate.opsForList().range(listKey, fromCursor, -1);
        if (rawEvents == null) {
            return List.of();
        }
        return rawEvents.stream()
                .map(Object::toString)
                .toList();
    }

    /**
     * Get all events from history list
     */
    public List<String> getAllEventsFromHistory(Long suggestionId) {
        return getEventsFromHistory(suggestionId, 0);
    }

    /**
     * Get total event count in history
     */
    public int getEventCount(Long suggestionId) {
        String listKey = getListKey(suggestionId);
        Long size = redisTemplate.opsForList().size(listKey);
        return size != null ? size.intValue() : 0;
    }

    /**
     * Cleanup Redis keys (optional manual cleanup)
     */
    public void cleanup(Long suggestionId) {
        redisTemplate.delete(getListKey(suggestionId));
        log.debug("Cleaned up Redis keys for suggestion {}", suggestionId);
    }
}
