package com.mygamehub.gamefinder;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "game_finder_maintenance_job")
public class GameFinderMaintenanceJob {
    @Id @Enumerated(EnumType.STRING) @Column(name = "job_type", length = 40)
    private MaintenanceJobType jobType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private MaintenanceJobStatus status = MaintenanceJobStatus.IDLE;
    private long processedCount;
    private long successCount;
    private long notFoundCount;
    private long retryableFailureCount;
    private long permanentFailureCount;
    private int consecutiveRateLimitCount;
    private Instant startedAt;
    private Instant updatedAt;
    private Instant nextRunAt;
    private Instant lastBatchStartedAt;
    private Instant lastBatchCompletedAt;
    private Long lastBatchDurationMs;
    @Column(length = 1000) private String lastError;
    private Instant stopRequestedAt;

    protected GameFinderMaintenanceJob() {}
    public GameFinderMaintenanceJob(MaintenanceJobType type) { this.jobType = type; touch(); }

    public void start(Instant now) {
        status = MaintenanceJobStatus.RUNNING; processedCount = successCount = notFoundCount = 0;
        retryableFailureCount = permanentFailureCount = 0; consecutiveRateLimitCount = 0;
        startedAt = now; nextRunAt = now; lastError = null; stopRequestedAt = null; touch(now);
    }
    public void resume(Instant now) { status = MaintenanceJobStatus.RUNNING; nextRunAt = null; touch(now); }
    public void iterationStarted(Instant now) { status = MaintenanceJobStatus.RUNNING; lastBatchStartedAt = now; nextRunAt = null; touch(now); }
    public void addBatch(long processed, long success, long notFound,
            long retryableFailure, long permanentFailure, long durationMs, Instant now) {
        processedCount += processed; successCount += success;
        notFoundCount += notFound; retryableFailureCount += retryableFailure;
        permanentFailureCount += permanentFailure; lastBatchDurationMs = durationMs;
        lastBatchCompletedAt = now; touch(now);
    }
    public void waitFor(MaintenanceJobStatus waiting, Instant next, boolean rateLimited, Instant now) {
        status = waiting; nextRunAt = next;
        consecutiveRateLimitCount = rateLimited ? consecutiveRateLimitCount + 1 : 0;
        touch(now);
    }
    public void normalBatch(Instant now) { consecutiveRateLimitCount = 0; touch(now); }
    public void requestStop(Instant now) { status = MaintenanceJobStatus.STOP_REQUESTED; stopRequestedAt = now; nextRunAt = null; touch(now); }
    public void stopped(Instant now) { status = MaintenanceJobStatus.STOPPED; nextRunAt = null; touch(now); }
    public void completed(Instant now) { status = MaintenanceJobStatus.COMPLETED; nextRunAt = null; consecutiveRateLimitCount = 0; touch(now); }
    public void failed(Throwable error, Instant now) {
        status = MaintenanceJobStatus.FAILED; nextRunAt = null;
        String value = error == null ? "Unknown" : error.getClass().getSimpleName();
        lastError = value.substring(0, Math.min(1000, value.length())); touch(now);
    }
    private void touch() { touch(Instant.now()); }
    private void touch(Instant now) { updatedAt = now; }

    public MaintenanceJobType getJobType() { return jobType; }
    public MaintenanceJobStatus getStatus() { return status; }
    public long getProcessedCount() { return processedCount; }
    public long getSuccessCount() { return successCount; }
    public long getNotFoundCount() { return notFoundCount; }
    public long getRetryableFailureCount() { return retryableFailureCount; }
    public long getPermanentFailureCount() { return permanentFailureCount; }
    public int getConsecutiveRateLimitCount() { return consecutiveRateLimitCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getNextRunAt() { return nextRunAt; }
    public Instant getLastBatchStartedAt() { return lastBatchStartedAt; }
    public Instant getLastBatchCompletedAt() { return lastBatchCompletedAt; }
    public Long getLastBatchDurationMs() { return lastBatchDurationMs; }
    public String getLastError() { return lastError; }
    public Instant getStopRequestedAt() { return stopRequestedAt; }
}
