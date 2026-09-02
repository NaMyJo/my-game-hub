package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameFinderAdminMaintenanceServiceTest {
    @Test
    void delegatesToExistingEnrichmentServiceAndReturnsCounts() {
        var sync = mock(SteamCatalogSyncService.class);
        when(sync.enrichBatch(1)).thenReturn(result(1));

        var response = new GameFinderAdminMaintenanceService(sync).tryEnrich(1).orElseThrow();

        assertThat(response.requestedBatchSize()).isEqualTo(1);
        assertThat(response.processed()).isEqualTo(1);
        verify(sync).enrichBatch(1);
    }

    @Test
    void rejectsSecondRequestWhileFirstIsRunning() throws Exception {
        var sync = mock(SteamCatalogSyncService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(sync.enrichBatch(1)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return result(1);
        });
        var service = new GameFinderAdminMaintenanceService(sync);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.tryEnrich(1));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

            assertThat(service.tryEnrich(1)).isEmpty();

            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isPresent();
        }
        verify(sync).enrichBatch(1);
    }

    @Test
    void catalogAndEnrichmentShareOneMaintenanceLock() throws Exception {
        var sync = mock(SteamCatalogSyncService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(sync.enrichBatch(1)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return result(1);
        });
        var service = new GameFinderAdminMaintenanceService(sync);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.tryEnrich(1));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.tryExpandCatalog(500)).isEmpty();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isPresent();
        }
    }

    @Test
    void fullCatalogSyncUsesSameMaintenanceLockAsEnrichment() throws Exception {
        var sync = mock(SteamCatalogSyncService.class);
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        when(sync.enrichBatch(1)).thenAnswer(invocation -> {
            entered.countDown();
            release.await(5, TimeUnit.SECONDS);
            return result(1);
        });
        var service = new GameFinderAdminMaintenanceService(sync);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> service.tryEnrich(1));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(service.tryFullCatalogSync()).isEmpty();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isPresent();
        }
    }

    private SteamCatalogSyncService.EnrichmentBatchResult result(int processed) {
        return new SteamCatalogSyncService.EnrichmentBatchResult(
                processed, processed, 0, 0, 0, processed, 0, 0, 0);
    }
}
