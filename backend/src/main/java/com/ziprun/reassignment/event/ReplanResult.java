package com.ziprun.reassignment.event;

import com.ziprun.reassignment.domain.enums.ReplanStatus;

import java.util.List;

public record ReplanResult(
        ReplanStatus status,
        String agentId,
        int totalOrders,
        int successCount,
        int failedCount,
        List<Long> createdSuggestionIds,
        List<Long> failedReplanIds
) {
    public static ReplanResult success(String agentId, int totalOrders, List<Long> suggestionIds) {
        return new ReplanResult(
                ReplanStatus.SUCCESS,
                agentId,
                totalOrders,
                totalOrders,
                0,
                suggestionIds,
                List.of()
        );
    }

    public static ReplanResult partial(String agentId, int totalOrders, int successCount,
                                        List<Long> suggestionIds, List<Long> failedIds) {
        return new ReplanResult(
                ReplanStatus.PARTIAL,
                agentId,
                totalOrders,
                successCount,
                totalOrders - successCount,
                suggestionIds,
                failedIds
        );
    }

    public static ReplanResult failed(String agentId, int totalOrders, List<Long> failedIds) {
        return new ReplanResult(
                ReplanStatus.FAILED,
                agentId,
                totalOrders,
                0,
                totalOrders,
                List.of(),
                failedIds
        );
    }

    public static ReplanResult noOrdersToReplan(String agentId) {
        return new ReplanResult(
                ReplanStatus.SUCCESS,
                agentId,
                0,
                0,
                0,
                List.of(),
                List.of()
        );
    }
}
