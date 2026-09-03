package com.mygamehub.gamefinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SteamMetadataMaintenanceRunnerTest {
    private final MaintenanceJobStateService states = mock(MaintenanceJobStateService.class);
    private final SteamCatalogSyncService sync = mock(SteamCatalogSyncService.class);
    private final GameFinderAdminMaintenanceService maintenance =
            mock(GameFinderAdminMaintenanceService.class);
    private final TaskScheduler scheduler = mock(TaskScheduler.class);
    private final AtomicReference<GameFinderMaintenanceJob> job = new AtomicReference<>();
    private final ArrayList<Runnable> scheduled = new ArrayList<>();
    private final Instant now = Instant.parse("2026-09-03T00:00:00Z");
    private SteamMetadataMaintenanceRunner runner;

    @BeforeEach
    void setUp() {
        job.set(new GameFinderMaintenanceJob(MaintenanceJobType.STEAM_METADATA));
        when(states.getOrCreate(MaintenanceJobType.STEAM_METADATA))
                .thenAnswer(invocation -> job.get());
        when(states.start(eq(MaintenanceJobType.STEAM_METADATA), any())).thenAnswer(invocation -> {
            job.get().start(invocation.getArgument(1)); return job.get();
        });
        when(states.save(any())).thenAnswer(invocation -> {
            job.set(invocation.getArgument(0)); return job.get();
        });
        when(maintenance.claimMetadataRunner()).thenReturn(true);
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            scheduled.add(invocation.getArgument(0)); return mock(ScheduledFuture.class);
        });
        when(sync.remainingInitialMetadataCandidates()).thenReturn(2L);
        runner = new SteamMetadataMaintenanceRunner(states, sync, maintenance, scheduler,
                40, 250, Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test
    void startSchedulesRepeatedBoundedBatchesWithoutAnotherHttpRequest() {
        var result = result(1, false, true);
        when(maintenance.tryMetadataRunnerBatch(40)).thenReturn(Optional.of(result));
        when(sync.remainingInitialMetadataCandidates()).thenReturn(1L, 1L, 0L, 0L);

        assertThat(runner.start()).isPresent();
        assertThat(scheduled).hasSize(1);
        scheduled.get(0).run();
        assertThat(scheduled).hasSize(2);
        scheduled.get(1).run();

        verify(maintenance, times(2)).tryMetadataRunnerBatch(40);
        assertThat(job.get().getStatus()).isEqualTo(MaintenanceJobStatus.COMPLETED);
        verify(maintenance).releaseMetadataRunner();
    }

    @Test
    void stopWhileWaitingCancelsNextRunAndDoesNotStartBatch() {
        runner.start();

        var response = runner.stop();

        assertThat(response.status()).isEqualTo("STOPPED");
        verify(maintenance, never()).tryMetadataRunnerBatch(anyInt());
        verify(maintenance).releaseMetadataRunner();
    }

    @Test
    void rateLimitedBatchWaitsAndIsAutomaticallyRescheduled() {
        when(maintenance.tryMetadataRunnerBatch(40))
                .thenReturn(Optional.of(result(3, true, true)));
        when(sync.remainingStoreRateLimitMs()).thenReturn(60_000L);
        runner.start();

        scheduled.get(0).run();

        assertThat(job.get().getStatus()).isEqualTo(MaintenanceJobStatus.WAITING_RATE_LIMIT);
        assertThat(job.get().getNextRunAt()).isEqualTo(now.plusSeconds(60));
        assertThat(job.get().getConsecutiveRateLimitCount()).isEqualTo(1);
        assertThat(scheduled).hasSize(2);
    }

    @Test
    void coolingRetryableCandidatesWaitUntilDatabaseTimestamp() {
        when(maintenance.tryMetadataRunnerBatch(40))
                .thenReturn(Optional.of(result(0, false, true)));
        when(sync.remainingInitialMetadataCandidates()).thenReturn(1L);
        when(sync.coolingMetadataRetryableCount()).thenReturn(1L);
        when(sync.nextMetadataRetryAt()).thenReturn(Optional.of(now.plusSeconds(300)));
        runner.start();

        scheduled.get(0).run();

        assertThat(job.get().getStatus()).isEqualTo(MaintenanceJobStatus.WAITING_RETRY);
        assertThat(job.get().getNextRunAt()).isEqualTo(now.plusSeconds(300));
    }

    @Test
    void duplicateStartDoesNotClaimOrScheduleAnotherRunner() {
        runner.start();

        assertThat(runner.start()).isEmpty();
        verify(maintenance, times(1)).claimMetadataRunner();
        assertThat(scheduled).hasSize(1);
    }

    @Test
    void recoveryConvertsStopRequestedToStoppedWithoutScheduling() {
        job.get().start(now); job.get().requestStop(now);

        runner.recover();

        assertThat(job.get().getStatus()).isEqualTo(MaintenanceJobStatus.STOPPED);
        verifyNoInteractions(scheduler);
    }

    private SteamCatalogSyncService.EnrichmentStageBatchResult result(
            int processed, boolean rateLimited, boolean more) {
        return new SteamCatalogSyncService.EnrichmentStageBatchResult(
                processed, processed, 0, 0, 0, more, rateLimited);
    }
}
