package com.ziprun.reassignment.event;

import com.ziprun.reassignment.domain.enums.ReplanStatus;
import com.ziprun.reassignment.service.ReplanningService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReplanningEventListener {

    private final ReplanningService replanningService;

    @Async("replanExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleAgentOffline(AgentOfflineEvent event) {
        log.info("Received AgentOfflineEvent for agent {} ({}), starting async replan",
                event.agentId(), event.agentName());

        try {
            ReplanResult result = replanningService.replanForAgent(event.agentId());

            logResult(event, result);

        } catch (Exception e) {
            log.error("Unexpected error during replan for agent {}: {}",
                    event.agentId(), e.getMessage(), e);
        }
    }

    private void logResult(AgentOfflineEvent event, ReplanResult result) {
        if (result.status() == ReplanStatus.SUCCESS) {
            if (result.totalOrders() == 0) {
                log.info("Replan complete for agent {}: no orders needed replanning",
                        event.agentId());
            } else {
                log.info("Replan SUCCESS for agent {}: {}/{} orders replanned, suggestions: {}",
                        event.agentId(),
                        result.successCount(),
                        result.totalOrders(),
                        result.createdSuggestionIds());
            }
        } else if (result.status() == ReplanStatus.PARTIAL) {
            log.warn("Replan PARTIAL for agent {}: {}/{} succeeded, {} failed. " +
                            "Suggestions: {}, FailedReplans: {}",
                    event.agentId(),
                    result.successCount(),
                    result.totalOrders(),
                    result.failedCount(),
                    result.createdSuggestionIds(),
                    result.failedReplanIds());
        } else {
            log.error("Replan FAILED for agent {}: all {} orders failed. FailedReplans: {}",
                    event.agentId(),
                    result.totalOrders(),
                    result.failedReplanIds());
        }
    }
}
