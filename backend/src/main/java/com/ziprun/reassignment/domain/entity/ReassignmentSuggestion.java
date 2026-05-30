package com.ziprun.reassignment.domain.entity;

import com.ziprun.reassignment.domain.enums.StreamStatus;
import com.ziprun.reassignment.domain.enums.SuggestionStatus;
import com.ziprun.reassignment.domain.enums.TriggerReason;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "reassignment_suggestions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReassignmentSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recommended_agent_id")
    private Agent recommendedAgent;

    private Double confidence;

    @Column(columnDefinition = "TEXT")
    private String reasoning;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SuggestionStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_reason", nullable = false)
    private TriggerReason triggerReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // ===== Streaming Fields =====

    @Enumerated(EnumType.STRING)
    @Column(name = "stream_status", nullable = false)
    @Builder.Default
    private StreamStatus streamStatus = StreamStatus.PROCESSING;

    @Column(name = "events_json", columnDefinition = "TEXT")
    private String eventsJson;

    @Column(name = "event_count")
    @Builder.Default
    private Integer eventCount = 0;

    @Column(name = "stream_started_at")
    private LocalDateTime streamStartedAt;

    @Column(name = "stream_completed_at")
    private LocalDateTime streamCompletedAt;

    @Column(name = "error_message")
    private String errorMessage;

    // ===== End Streaming Fields =====

    // ===== Rejection Chain Fields =====

    @Column(name = "parent_suggestion_id")
    private Long parentSuggestionId;

    @Column(name = "rejection_feedback", length = 1000)
    private String rejectionFeedback;

    // ===== End Rejection Chain Fields =====

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (streamStartedAt == null) {
            streamStartedAt = LocalDateTime.now();
        }
    }
}
