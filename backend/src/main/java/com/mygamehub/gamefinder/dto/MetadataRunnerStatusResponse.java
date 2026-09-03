package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.GameFinderMaintenanceJob;
import java.time.Instant;

public record MetadataRunnerStatusResponse(
        String status, long processedCount, long successCount, long notFoundCount,
        long retryableFailureCount, long permanentFailureCount,
        Instant startedAt, Instant updatedAt, Instant nextRunAt,
        Long lastBatchDurationMs, int consecutiveRateLimitCount,
        long remainingMetadataCandidates, long cooldownRetryableCount,
        boolean initialPopulationComplete, String lastError) {
    public static MetadataRunnerStatusResponse from(GameFinderMaintenanceJob job,
            long remaining, long cooling) {
        return new MetadataRunnerStatusResponse(job.getStatus().name(), job.getProcessedCount(),
                job.getSuccessCount(), job.getNotFoundCount(), job.getRetryableFailureCount(),
                job.getPermanentFailureCount(), job.getStartedAt(), job.getUpdatedAt(),
                job.getNextRunAt(), job.getLastBatchDurationMs(),
                job.getConsecutiveRateLimitCount(), remaining, cooling,
                remaining == 0, job.getLastError());
    }
}
