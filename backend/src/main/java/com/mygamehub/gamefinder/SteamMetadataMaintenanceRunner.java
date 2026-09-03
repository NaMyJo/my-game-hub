package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.MetadataRunnerStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.Optional;

@Service
public class SteamMetadataMaintenanceRunner {
    private static final Logger log = LoggerFactory.getLogger(SteamMetadataMaintenanceRunner.class);
    private static final MaintenanceJobType TYPE = MaintenanceJobType.STEAM_METADATA;
    private final MaintenanceJobStateService states;
    private final SteamCatalogSyncService sync;
    private final GameFinderAdminMaintenanceService maintenance;
    private final TaskScheduler scheduler;
    private final int batchSize;
    private final long iterationDelayMs;
    private final Clock clock;
    private final AtomicBoolean iterationRunning = new AtomicBoolean(false);
    private final Object scheduleMonitor = new Object();
    private ScheduledFuture<?> scheduled;

    @Autowired
    public SteamMetadataMaintenanceRunner(MaintenanceJobStateService states,
            SteamCatalogSyncService sync, GameFinderAdminMaintenanceService maintenance,
            @Qualifier("gameFinderMaintenanceTaskScheduler") TaskScheduler scheduler,
            @Value("${app.game-finder.metadata-runner-batch-size:40}") int batchSize,
            @Value("${app.game-finder.metadata-runner-iteration-delay-ms:250}") long iterationDelayMs) {
        this(states, sync, maintenance, scheduler, batchSize, iterationDelayMs, Clock.systemUTC());
    }

    SteamMetadataMaintenanceRunner(MaintenanceJobStateService states,
            SteamCatalogSyncService sync, GameFinderAdminMaintenanceService maintenance,
            TaskScheduler scheduler, int batchSize, long iterationDelayMs, Clock clock) {
        this.states = states; this.sync = sync; this.maintenance = maintenance;
        this.scheduler = scheduler; this.batchSize = Math.max(1, Math.min(40, batchSize));
        this.iterationDelayMs = Math.max(100, iterationDelayMs); this.clock = clock;
    }

    public synchronized Optional<MetadataRunnerStatusResponse> start() {
        var current = states.getOrCreate(TYPE);
        if (current.getStatus().active() || !maintenance.claimMetadataRunner()) return Optional.empty();
        var job = states.start(TYPE, now());
        log.info("game_finder_metadata_runner_started batchSize={}", batchSize);
        schedule(now());
        return Optional.of(snapshot(job));
    }

    public synchronized MetadataRunnerStatusResponse stop() {
        var job = states.getOrCreate(TYPE);
        if (!job.getStatus().active()) return snapshot(job);
        job.requestStop(now()); states.save(job);
        log.info("game_finder_metadata_runner_stop_requested");
        if (!iterationRunning.get()) finishStopped(job);
        return snapshot(states.getOrCreate(TYPE));
    }

    public MetadataRunnerStatusResponse status() { return snapshot(states.getOrCreate(TYPE)); }

    @EventListener(ApplicationReadyEvent.class)
    public synchronized void recover() {
        var job = states.getOrCreate(TYPE);
        if (job.getStatus() == MaintenanceJobStatus.STOP_REQUESTED) {
            job.stopped(now()); states.save(job); return;
        }
        if (!job.getStatus().active() || !maintenance.claimMetadataRunner()) return;
        Instant runAt = job.getNextRunAt() == null ? now() : laterOf(now(), job.getNextRunAt());
        log.info("game_finder_metadata_runner_resumed status={} nextRunAt={}",
                job.getStatus(), runAt);
        schedule(runAt);
    }

    void runOneIteration() {
        if (!iterationRunning.compareAndSet(false, true)) return;
        try {
            var job = states.getOrCreate(TYPE);
            if (job.getStatus() == MaintenanceJobStatus.STOP_REQUESTED) {
                finishStopped(job); return;
            }
            if (!job.getStatus().active()) { maintenance.releaseMetadataRunner(); return; }
            Instant started = now(); job.iterationStarted(started); states.save(job);
            log.info("game_finder_metadata_runner_iteration_start batchSize={}", batchSize);
            long startedNanos = System.nanoTime();
            var batch = maintenance.tryMetadataRunnerBatch(batchSize);
            if (batch.isEmpty()) { schedule(now().plusMillis(1000)); return; }
            var result = batch.orElseThrow();
            long durationMs = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            job = states.getOrCreate(TYPE);
            job.addBatch(result.processed(), result.success(), result.notFound(),
                    result.retryableFailure(), result.permanentFailure(), durationMs, now());
            states.save(job);
            log.info("game_finder_metadata_runner_iteration_complete processed={} durationMs={} rateLimited={}",
                    result.processed(), durationMs, result.rateLimited());
            if (job.getStatus() == MaintenanceJobStatus.STOP_REQUESTED) {
                finishStopped(job); return;
            }
            if (result.rateLimited()) { waitRateLimit(job); return; }
            job.normalBatch(now()); states.save(job);
            long remaining = sync.remainingInitialMetadataCandidates();
            if (remaining == 0) { finishCompleted(job); return; }
            if (result.processed() == 0) {
                long cooling = sync.coolingMetadataRetryableCount();
                if (cooling > 0) { waitRetry(job); return; }
                finishCompleted(job); return;
            }
            schedule(now().plusMillis(iterationDelayMs));
        } catch (RuntimeException error) {
            var job = states.getOrCreate(TYPE); job.failed(error, now()); states.save(job);
            maintenance.releaseMetadataRunner();
            log.error("game_finder_metadata_runner_failed errorType={}",
                    error.getClass().getSimpleName());
        } finally {
            iterationRunning.set(false);
        }
    }

    private void waitRateLimit(GameFinderMaintenanceJob job) {
        int consecutive = job.getConsecutiveRateLimitCount();
        long tierMs = consecutive == 0 ? 60_000 : consecutive == 1 ? 120_000 : 300_000;
        long delay = Math.max(tierMs, sync.remainingStoreRateLimitMs());
        Instant next = now().plusMillis(delay);
        job.waitFor(MaintenanceJobStatus.WAITING_RATE_LIMIT, next, true, now()); states.save(job);
        log.info("game_finder_metadata_runner_wait reason=RATE_LIMIT nextRunAt={} delayMs={}", next, delay);
        schedule(next);
    }

    private void waitRetry(GameFinderMaintenanceJob job) {
        Instant next = sync.nextMetadataRetryAt().map(value -> laterOf(now(), value))
                .orElseGet(() -> now().plusSeconds(30));
        job.waitFor(MaintenanceJobStatus.WAITING_RETRY, next, false, now()); states.save(job);
        log.info("game_finder_metadata_runner_wait reason=RETRY_COOLDOWN nextRunAt={} delayMs={}",
                next, Math.max(0, Duration.between(now(), next).toMillis()));
        schedule(next);
    }

    private void finishStopped(GameFinderMaintenanceJob job) {
        cancelScheduled(); job.stopped(now()); states.save(job); maintenance.releaseMetadataRunner();
        log.info("game_finder_metadata_runner_stopped");
    }
    private void finishCompleted(GameFinderMaintenanceJob job) {
        cancelScheduled(); job.completed(now()); states.save(job); maintenance.releaseMetadataRunner();
        log.info("game_finder_metadata_runner_completed");
    }
    private void schedule(Instant instant) {
        synchronized (scheduleMonitor) {
            if (scheduled != null && !scheduled.isDone()) scheduled.cancel(false);
            scheduled = scheduler.schedule(this::runOneIteration, instant);
        }
    }
    private void cancelScheduled() {
        synchronized (scheduleMonitor) {
            if (scheduled != null && !scheduled.isDone()) scheduled.cancel(false);
            scheduled = null;
        }
    }
    private MetadataRunnerStatusResponse snapshot(GameFinderMaintenanceJob job) {
        return MetadataRunnerStatusResponse.from(job, sync.remainingInitialMetadataCandidates(),
                sync.coolingMetadataRetryableCount());
    }
    private Instant now() { return clock.instant(); }
    private static Instant laterOf(Instant left, Instant right) {
        return left.isAfter(right) ? left : right;
    }
}
