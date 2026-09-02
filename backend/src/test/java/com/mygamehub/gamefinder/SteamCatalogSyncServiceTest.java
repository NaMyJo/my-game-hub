package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SteamCatalogSyncServiceTest {
    private final SteamCatalogClient catalog = mock(SteamCatalogClient.class);
    private final SteamStoreDetailClient store = mock(SteamStoreDetailClient.class);
    private final SteamGameRepository games = mock(SteamGameRepository.class);
    private final SteamCatalogPersistenceService persistence = mock(SteamCatalogPersistenceService.class);
    private final CatalogSyncCheckpointRepository checkpoints = mock(CatalogSyncCheckpointRepository.class);
    private final IgdbEnrichmentClient igdb = mock(IgdbEnrichmentClient.class);
    private final GameTagService tagService = mock(GameTagService.class);
    private final SteamCatalogSyncService service =
            new SteamCatalogSyncService(catalog, store, games, persistence, checkpoints,
                    igdb, tagService, 10, 2, 0, 0, 0);

    @Test
    void configuredBatchSizeIsUsedByEnrichmentQuery() {
        var single = new SteamCatalogSyncService(catalog, store, games, persistence,
                checkpoints, igdb, tagService, 1, 2, 0, 0, 0);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of());

        single.enrichBatch();

        verify(games).findMetadataCandidates(any(), argThat(pageable -> pageable.getPageSize() == 1));
        verify(games).findIgdbCandidates(argThat(pageable -> pageable.getPageSize() == 1));
    }

    @Test
    void enrichmentResponseUsesActualCandidateQueryForCompletion() {
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of());
        when(games.countEnrichmentCandidates(any())).thenReturn(0L, 3L);
        when(igdb.configured()).thenReturn(true);

        var completed = service.enrichBatch(1);
        var more = service.enrichBatch(1);

        assertEquals(0, completed.processed());
        assertFalse(completed.hasMoreCandidates());
        assertTrue(more.hasMoreCandidates());
    }

    @Test
    void metadataStageDoesNotCallIgdb() {
        var pending = new SteamGame(570, "Dota 2", 1, 1);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of(pending));
        when(store.get(570)).thenReturn(Optional.of(detail("game")));
        when(games.countMetadataCandidates(any())).thenReturn(0L);

        var result = service.enrichMetadataBatch(1);

        assertEquals(1, result.processed());
        assertEquals(1, result.success());
        verifyNoInteractions(igdb);
        verify(store).get(570);
    }

    @Test
    void metadataConcurrencyIsBoundedAndStillPersistsEveryApp() {
        var concurrentService = new SteamCatalogSyncService(catalog, store, games, persistence,
                checkpoints, igdb, tagService, 40, 2, 0, 500, 2, 260);
        var first = new SteamGame(570, "Dota 2", 1, 1);
        var second = new SteamGame(1245620, "ELDEN RING", 1, 1);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of(first, second));
        when(store.get(anyLong())).thenReturn(Optional.of(detail("game")));
        when(games.countMetadataCandidates(any())).thenReturn(0L);

        var result = concurrentService.enrichMetadataBatch(40);

        assertEquals(2, result.processed());
        verify(store).get(570);
        verify(store).get(1245620);
        verify(games).save(first);
        verify(games).save(second);
    }

    @Test
    void reversedResponsesRemainAttachedToTheirRequestedApps() throws Exception {
        var concurrentService = new SteamCatalogSyncService(catalog, store, games, persistence,
                checkpoints, igdb, tagService, 40, 2, 0, 500, 2, 260);
        var first = new SteamGame(10, "catalog-a", 1, 1);
        var second = new SteamGame(20, "catalog-b", 1, 1);
        var bothRequested = new CountDownLatch(2);
        var secondSaved = new CountDownLatch(1);
        var snapshots = new ConcurrentHashMap<Long, MetadataSnapshot>();
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of(first, second));
        when(games.countMetadataCandidates(any())).thenReturn(0L);
        when(store.get(anyLong())).thenAnswer(invocation -> {
            long appId = invocation.getArgument(0);
            bothRequested.countDown();
            assertTrue(bothRequested.await(2, TimeUnit.SECONDS));
            if (appId == 10L) assertTrue(secondSaved.await(2, TimeUnit.SECONDS));
            return Optional.of(appId == 10L
                    ? detailFor(10, "Store A", "game", 1000)
                    : detailFor(20, "Store B", "game", 2000));
        });
        when(games.save(any())).thenAnswer(invocation -> {
            SteamGame game = invocation.getArgument(0);
            snapshots.put(game.getSteamAppId(), new MetadataSnapshot(game.getName(),
                    game.getStoreType(), game.getPriceCurrent(), game.getMetadataStatus()));
            if (game.getSteamAppId() == 20L) secondSaved.countDown();
            return game;
        });

        var result = concurrentService.enrichMetadataBatch(40);

        assertEquals(2, result.processed());
        assertEquals(new MetadataSnapshot("Store A", "game", 1000,
                EnrichmentStatus.SUCCESS), snapshots.get(10L));
        assertEquals(new MetadataSnapshot("Store B", "game", 2000,
                EnrichmentStatus.SUCCESS), snapshots.get(20L));
    }

    @Test
    void duplicateCandidateAppIdIsAssignedToOnlyOneWorker() {
        var concurrentService = new SteamCatalogSyncService(catalog, store, games, persistence,
                checkpoints, igdb, tagService, 40, 2, 0, 500, 2, 260);
        var duplicate = new SteamGame(570, "Dota 2", 1, 1);
        when(games.findMetadataCandidates(any(), any()))
                .thenReturn(List.of(duplicate, duplicate));
        when(store.get(570)).thenReturn(Optional.of(
                detailFor(570, "Dota 2", "game", 0)));
        when(games.countMetadataCandidates(any())).thenReturn(0L);

        var result = concurrentService.enrichMetadataBatch(40);

        assertEquals(1, result.processed());
        verify(store, times(1)).get(570);
        verify(games, times(1)).save(duplicate);
    }

    @Test
    void oneWorkerFailureDoesNotChangeTheOtherAppsSuccess() {
        var concurrentService = new SteamCatalogSyncService(catalog, store, games, persistence,
                checkpoints, igdb, tagService, 40, 2, 0, 500, 2, 260);
        var failed = new SteamGame(10, "A", 1, 1);
        var succeeded = new SteamGame(20, "B", 1, 1);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of(failed, succeeded));
        when(store.get(10)).thenThrow(new IllegalStateException("network"));
        when(store.get(20)).thenReturn(Optional.of(detailFor(20, "Store B", "game", 2000)));
        when(games.countMetadataCandidates(any())).thenReturn(1L);

        var result = concurrentService.enrichMetadataBatch(40);

        assertEquals(EnrichmentStatus.RETRYABLE_FAILURE, failed.getMetadataStatus());
        assertEquals(EnrichmentStatus.SUCCESS, succeeded.getMetadataStatus());
        assertEquals("Store B", succeeded.getName());
        assertEquals(2000, succeeded.getPriceCurrent());
        assertEquals(1, result.success());
        assertEquals(1, result.retryableFailure());
    }

    @Test
    void igdbStageUsesOneBatchClientCallAndDoesNotCallSteamStore() {
        var first = gameWithCurrentMetadata(570, "Dota 2", "game");
        var second = gameWithCurrentMetadata(1245620, "ELDEN RING", "game");
        when(games.findIgdbCandidates(any())).thenReturn(List.of(first, second));
        when(igdb.configured()).thenReturn(true);
        when(igdb.findBySteamAppIds(List.of(570L, 1245620L))).thenReturn(java.util.Map.of(
                570L, Optional.of(new IgdbEnrichmentClient.IgdbData(
                        42L, 1, 1, 10, 10, 5, true, true, false)),
                1245620L, Optional.empty()));
        when(persistence.applyIgdbResults(eq(List.of(570L, 1245620L)), anyMap()))
                .thenAnswer(invocation -> {
                    first.updateIgdb(42L, 1, 10, 10, 5, true, true, false);
                    second.markIgdbNotFound();
                    return List.of(first, second);
                });
        when(games.countIgdbCandidates()).thenReturn(0L);

        var result = service.enrichIgdbBatch(40);

        assertEquals(2, result.processed());
        assertEquals(1, result.success());
        assertEquals(1, result.notFound());
        verify(igdb).findBySteamAppIds(List.of(570L, 1245620L));
        verifyNoInteractions(store);
    }

    @Test
    void adminCatalogExpansionIsNoOpWhenTargetIsAlreadyReached() {
        when(games.count()).thenReturn(1000L);

        var result = service.expandCatalogTo(500);

        assertTrue(result.targetReached());
        assertEquals(0, result.fetched());
        verifyNoInteractions(catalog);
        verify(checkpoints, never()).save(any());
    }

    @Test
    void adminCatalogExpansionProcessesOnlyBoundedRemainingChunk() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-expand");
        var items = java.util.stream.LongStream.rangeClosed(101, 500)
                .mapToObj(id -> new SteamCatalogClient.CatalogItem(id, "Game " + id, 1, 1))
                .toList();
        when(games.count()).thenReturn(100L, 500L);
        when(checkpoints.findById("steam-catalog-admin-expand"))
                .thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 400)).thenReturn(
                new SteamCatalogClient.CatalogPage(items, true, 500));
        when(persistence.upsertAll(items)).thenReturn(items.stream()
                .map(item -> new SteamGame(item.appId(), item.name(), 1, 1)).toList());

        var result = service.expandCatalogTo(500);

        assertEquals(400, result.fetched());
        assertEquals(400, result.newlySaved());
        assertEquals(500, result.currentTotal());
        assertTrue(result.targetReached());
        assertEquals(500, checkpoint.getLastAppId());
        verify(games, never()).markMissingAsRemoved(anyString());
        verifyNoInteractions(store, igdb);
    }

    @Test
    void tenThousandTargetStillFetchesAtMostFiveHundredApps() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-expand");
        when(games.count()).thenReturn(100L, 600L);
        when(checkpoints.findById("steam-catalog-admin-expand"))
                .thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(), true, 0));

        service.expandCatalogTo(10000);

        verify(catalog).page(0, null, 500);
    }

    @Test
    void expansionFromNineHundredToOneThousandRequestsOnlyOneHundred() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-expand");
        checkpoint.progress(900);
        when(games.count()).thenReturn(900L, 1000L);
        when(checkpoints.findById("steam-catalog-admin-expand"))
                .thenReturn(Optional.of(checkpoint));
        when(catalog.page(900, null, 100)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(), true, 900));

        var result = service.expandCatalogTo(1000);

        verify(catalog).page(900, null, 100);
        assertTrue(result.targetReached());
    }

    @Test
    void adminCatalogCheckpointDoesNotAdvanceWhenPersistenceFails() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-expand");
        var item = new SteamCatalogClient.CatalogItem(10, "A", 1, 1);
        when(games.count()).thenReturn(0L);
        when(checkpoints.findById("steam-catalog-admin-expand"))
                .thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 10));
        when(persistence.upsertAll(List.of(item))).thenThrow(new IllegalStateException("db"));

        assertThrows(IllegalStateException.class, () -> service.expandCatalogTo(500));

        assertEquals(0, checkpoint.getLastAppId());
        assertEquals("FAILED", checkpoint.getStatus());
    }

    @Test
    void fullCatalogSyncPersistsFirstBoundedPageAndAdvancesDedicatedCheckpoint() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        var item = new SteamCatalogClient.CatalogItem(500, "App", 1, 1);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(Optional.of(checkpoint));
        when(games.count()).thenReturn(100L, 101L);
        when(catalog.pageAllApps(0, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 500));
        when(persistence.upsertAll(List.of(item))).thenReturn(
                List.of(new SteamGame(500, "App", 1, 1)));

        var result = service.syncFullCatalogPage();

        assertEquals(1, result.fetched());
        assertEquals(1, result.discoveredCount());
        assertFalse(result.completed());
        assertEquals(500, checkpoint.getLastAppId());
        verify(games, never()).markMissingAsRemoved(anyString());
        verifyNoInteractions(store, igdb);
    }

    @Test
    void fullCatalogSyncResumesFromPreviousLastAppId() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        checkpoint.fullSyncPage(500, 500, false);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(Optional.of(checkpoint));
        var item = new SteamCatalogClient.CatalogItem(600, "Next", 1, 1);
        when(games.count()).thenReturn(500L, 501L);
        when(catalog.pageAllApps(500, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 600));

        var result = service.syncFullCatalogPage();

        verify(catalog).pageAllApps(500, 500);
        assertEquals(501, result.discoveredCount());
    }

    @Test
    void fullCatalogSyncMarksCompletedFromSteamHaveMoreFlag() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        checkpoint.fullSyncPage(500, 500, false);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(Optional.of(checkpoint));
        when(games.count()).thenReturn(500L, 500L);
        when(catalog.pageAllApps(500, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(), false, 500));

        var result = service.syncFullCatalogPage();

        assertTrue(result.completed());
        assertEquals("COMPLETED", checkpoint.getStatus());
    }

    @Test
    void completedFullCatalogSyncIsNoOp() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        checkpoint.fullSyncPage(12345, 12000, true);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(Optional.of(checkpoint));
        when(games.count()).thenReturn(11000L);

        var result = service.syncFullCatalogPage();

        assertTrue(result.completed());
        assertEquals(0, result.fetched());
        assertEquals(12000, result.discoveredCount());
        verifyNoInteractions(catalog);
    }

    @Test
    void fullCatalogPersistenceFailureKeepsCursorAndDiscoveredCount() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        checkpoint.fullSyncPage(500, 500, false);
        var item = new SteamCatalogClient.CatalogItem(600, "App", 1, 1);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(Optional.of(checkpoint));
        when(games.count()).thenReturn(500L);
        when(catalog.pageAllApps(500, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 600));
        when(persistence.upsertAll(List.of(item))).thenThrow(new IllegalStateException("db"));

        assertThrows(IllegalStateException.class, service::syncFullCatalogPage);

        assertEquals(500, checkpoint.getLastAppId());
        assertEquals(500, checkpoint.getProcessedCount());
        assertEquals("FAILED", checkpoint.getStatus());
    }

    @Test
    void fullAndTargetExpansionUseIndependentCheckpoints() {
        var full = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        full.progress(700);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(Optional.of(full));
        var item = new SteamCatalogClient.CatalogItem(800, "Next", 1, 1);
        when(games.count()).thenReturn(700L, 701L);
        when(catalog.pageAllApps(700, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 800));

        service.syncFullCatalogPage();

        verify(checkpoints, never()).findById("steam-catalog-admin-expand");
    }

    @Test
    void gameOnlyScanUsesIndependentCheckpointAndEligibilityUpsert() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-game-only");
        var item = new SteamCatalogClient.CatalogItem(570, "Dota 2", 1, 1);
        when(checkpoints.findById("steam-catalog-admin-game-only"))
                .thenReturn(Optional.of(checkpoint));
        when(games.countByGameCatalogEligibleTrue()).thenReturn(0L, 1L);
        when(catalog.page(0, null, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 570));

        var result = service.syncGameOnlyCatalogPage();

        verify(persistence).upsertGameCatalogAll(List.of(item));
        verify(catalog, never()).pageAllApps(anyLong(), anyInt());
        verifyNoInteractions(store, igdb);
        assertEquals(570, checkpoint.getLastAppId());
        assertEquals(1, result.eligibleCatalogTotal());
        assertFalse(result.completed());
    }

    @Test
    void gameOnlyScanResumesAndCompletesFromSteamContinuationFlag() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-game-only");
        checkpoint.fullSyncPage(570, 500, false);
        when(checkpoints.findById("steam-catalog-admin-game-only"))
                .thenReturn(Optional.of(checkpoint));
        when(games.countByGameCatalogEligibleTrue()).thenReturn(500L, 500L);
        when(catalog.page(570, null, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(), false, 570));

        var result = service.syncGameOnlyCatalogPage();

        assertTrue(result.completed());
        assertEquals("COMPLETED", checkpoint.getStatus());
        assertEquals(500, result.discoveredCount());
    }

    @Test
    void gameOnlyPersistenceFailureDoesNotAdvanceCursor() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-game-only");
        checkpoint.fullSyncPage(570, 500, false);
        var item = new SteamCatalogClient.CatalogItem(730, "Next", 1, 1);
        when(checkpoints.findById("steam-catalog-admin-game-only"))
                .thenReturn(Optional.of(checkpoint));
        when(games.countByGameCatalogEligibleTrue()).thenReturn(500L);
        when(catalog.page(570, null, 500)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 730));
        when(persistence.upsertGameCatalogAll(List.of(item)))
                .thenThrow(new IllegalStateException("db"));

        assertThrows(IllegalStateException.class, service::syncGameOnlyCatalogPage);

        assertEquals(570, checkpoint.getLastAppId());
        assertEquals(500, checkpoint.getProcessedCount());
        assertEquals("FAILED", checkpoint.getStatus());
    }

    @Test
    void completedGameOnlyScanIsNoOp() {
        var checkpoint = new CatalogSyncCheckpoint("steam-catalog-admin-game-only");
        checkpoint.fullSyncPage(999, 1200, true);
        when(checkpoints.findById("steam-catalog-admin-game-only"))
                .thenReturn(Optional.of(checkpoint));
        when(games.countByGameCatalogEligibleTrue()).thenReturn(1000L);

        var result = service.syncGameOnlyCatalogPage();

        assertTrue(result.completed());
        assertEquals(0, result.fetched());
        verifyNoInteractions(catalog);
    }

    @BeforeEach
    void persistenceReturnsEntities() {
        lenient().when(persistence.upsertAll(anyCollection())).thenAnswer(invocation ->
                ((java.util.Collection<SteamCatalogClient.CatalogItem>) invocation.getArgument(0))
                        .stream().map(item -> new SteamGame(item.appId(), item.name(),
                                item.lastModified(), item.priceChangeNumber())).toList());
    }

    @Test
    void paginationPersistsProgressWithoutAdvancingModifiedCheckpoint() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(10, "A", 100, 1)), true, 10));
        assertTrue(service.sync("steam-catalog"));

        assertEquals(10, checkpoint.getLastAppId());
        assertNull(checkpoint.getLastModifiedSince());
        assertEquals(100, checkpoint.getPendingMaxModified());
        assertEquals("RUNNING", checkpoint.getStatus());
        verify(persistence).upsertAll(anyCollection());
    }

    @Test
    void completedPageAdvancesModifiedCheckpointAndResetsCursor() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(20, "B", 200, 2)), false, 20));
        assertFalse(service.sync("steam-catalog"));

        assertEquals(0, checkpoint.getLastAppId());
        assertEquals(200, checkpoint.getLastModifiedSince());
        assertEquals("SUCCESS", checkpoint.getStatus());
        assertNotNull(checkpoint.getLastSuccessfulSyncAt());
    }

    @Test
    void resumeKeepsMaximumModifiedValueAcrossPages() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(10, "A", 900, 1)), true, 10));
        when(catalog.page(10, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(20, "B", 200, 2)), false, 20));
        assertTrue(service.sync("steam-catalog"));
        assertFalse(service.sync("steam-catalog"));

        assertEquals(900, checkpoint.getLastModifiedSince());
        assertNull(checkpoint.getPendingMaxModified());
    }

    @Test
    void failedPageDoesNotAdvanceCheckpoint() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenThrow(new IllegalStateException("rate limited"));

        assertThrows(IllegalStateException.class, () -> service.sync("steam-catalog"));

        assertNull(checkpoint.getLastModifiedSince());
        assertEquals("FAILED", checkpoint.getStatus());
        assertEquals("rate limited", checkpoint.getFailureInfo());
    }

    @Test
    void existingCatalogRowIsUpdatedInsteadOfDuplicated() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(30, "New", 300, 3)), false, 30));
        service.sync("steam-catalog");

        verify(persistence).upsertAll(argThat(values -> values.iterator().next().appId() == 30L));
    }

    @Test
    void rowFailureDoesNotAdvancePageCheckpoint() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(40, "Broken", 400, 4)), true, 40));
        when(persistence.upsertAll(anyCollection()))
                .thenThrow(new IllegalStateException("db unavailable"));

        assertThrows(IllegalStateException.class, () -> service.sync("steam-catalog"));

        assertEquals(0, checkpoint.getLastAppId());
        assertEquals("FAILED", checkpoint.getStatus());
    }

    @Test
    void limitedBootstrapDoesNotSaveCheckpoint() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 100)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(), true, 100));

        assertEquals(0, service.bootstrapLimited(100));

        verify(checkpoints, never()).save(any());
        assertEquals(0, checkpoint.getLastAppId());
        assertEquals("NEW", checkpoint.getStatus());
    }

    @Test
    void limitedBootstrapPersistsAllOneHundredBeforeStartingEnrichment() {
        SteamCatalogSyncService limitedService = new SteamCatalogSyncService(
                catalog, store, games, persistence, checkpoints, igdb, tagService,
                10, 2, 100, 0, 0);
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        List<SteamCatalogClient.CatalogItem> items = new ArrayList<>();
        List<SteamGame> saved = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long appId = 10L + i;
            items.add(new SteamCatalogClient.CatalogItem(appId, "Game " + appId, i, i));
            saved.add(new SteamGame(appId, "Game " + appId, i, i));
        }
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 100)).thenReturn(
                new SteamCatalogClient.CatalogPage(items, true, 109));
        when(persistence.upsertAll(items)).thenReturn(saved);
        when(store.get(anyLong())).thenReturn(Optional.empty());

        assertEquals(100, limitedService.bootstrapLimited(100));

        InOrder order = inOrder(persistence, store);
        order.verify(persistence).upsertAll(items);
        order.verify(store, times(100)).get(anyLong());
        verify(checkpoints, never()).save(any());
        assertEquals(0, checkpoint.getLastAppId());
    }

    @Test
    void catalogPersistDiagnosticDoesNotEnrichOrChangeCheckpoint() {
        SteamCatalogSyncService diagnosticService = new SteamCatalogSyncService(
                catalog, store, games, persistence, checkpoints, igdb, tagService,
                10, 2, 100, 0, 0);
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        var item = new SteamCatalogClient.CatalogItem(10, "Counter-Strike", 1, 1);
        var saved = new SteamGame(10, "Counter-Strike", 1, 1);
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 100)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 10));
        when(persistence.upsertAll(List.of(item))).thenReturn(List.of(saved));

        assertEquals(1, diagnosticService.catalogPersistDiagnostic());

        verify(store, never()).get(anyLong());
        verifyNoInteractions(igdb);
        verify(checkpoints, never()).save(any());
        assertEquals("NEW", checkpoint.getStatus());
        assertEquals(0, checkpoint.getLastAppId());
    }

    @Test
    void resumeBatchSkipsCompletedMetadataAndProcessesPendingOnly() {
        SteamGame completed = new SteamGame(1, "Done", 0, 0);
        completed.updateStoreDetail("game", null, null, false, "KRW", 0, 0, 0,
                0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, false, false, false);
        SteamGame pending = new SteamGame(24, "Pending", 0, 0);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of(pending));
        when(games.findIgdbCandidates(any())).thenReturn(List.of());
        when(store.get(24L)).thenReturn(Optional.empty());

        assertEquals(1, service.enrichBatch());

        verify(store).get(24L);
        verify(store, never()).get(1L);
        assertEquals(EnrichmentStatus.NOT_FOUND, pending.getMetadataStatus());
        assertEquals(EnrichmentStatus.SUCCESS, completed.getMetadataStatus());
    }

    @Test
    void igdbNotFoundIsDurableAndSuccessIsNotSelectedAgain() {
        SteamGame noMatch = new SteamGame(4436560, "No Match", 0, 0);
        noMatch.updateStoreDetail("game", null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, true, false, false);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of(noMatch));
        when(igdb.configured()).thenReturn(true);
        when(igdb.findBySteamAppId(4436560L)).thenReturn(Optional.empty());

        assertEquals(1, service.enrichBatch());

        assertEquals(EnrichmentStatus.NOT_FOUND, noMatch.getIgdbStatus());
        assertNotNull(noMatch.getIgdbUpdatedAt());
        verify(igdb, times(1)).findBySteamAppId(4436560L);
    }

    @Test
    void nonGameMetadataIsMarkedIgdbNotApplicableWithoutIgdbCall() {
        SteamGame dlc = new SteamGame(99, "DLC", 0, 0);
        dlc.updateStoreDetail("dlc", null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                false, false, false, false);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of(dlc));
        when(igdb.configured()).thenReturn(true);

        var result = service.enrichBatch(1);

        assertEquals(1, result.processed());
        assertEquals(EnrichmentStatus.NOT_FOUND, dlc.getIgdbStatus());
        verify(igdb, never()).findBySteamAppId(anyLong());
    }

    @Test
    void gameMetadataStillUsesIgdbEnrichment() {
        SteamGame game = new SteamGame(570, "Game", 0, 0);
        game.updateStoreDetail("game", null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, true, false, false);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of(game));
        when(igdb.configured()).thenReturn(true);
        when(igdb.findBySteamAppId(570L)).thenReturn(Optional.empty());

        service.enrichBatch(1);

        verify(igdb).findBySteamAppId(570L);
    }

    @Test
    void retryableIgdbFailureCanSucceedOnNextBatch() {
        SteamGame game = new SteamGame(570, "Dota 2", 0, 0);
        game.updateStoreDetail("game", null, null, true, "KRW", 0, 0, 0, 0,
                "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                false, true, true, false);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of(game));
        when(igdb.configured()).thenReturn(true);
        when(igdb.findBySteamAppId(570L))
                .thenThrow(new IgdbEnrichmentClient.IgdbRequestException("external_games", 429, 0L))
                .thenReturn(Optional.of(new IgdbEnrichmentClient.IgdbData(
                        42L, 1, 1, 10, 10, 5, true, true, false)));

        service.enrichBatch();
        assertEquals(EnrichmentStatus.RETRYABLE_FAILURE, game.getIgdbStatus());
        service.enrichBatch();

        assertEquals(EnrichmentStatus.SUCCESS, game.getIgdbStatus());
        assertEquals(42L, game.getIgdbGameId());
        verify(igdb, times(2)).findBySteamAppId(570L);
    }

    @Test
    void legacyMetadataWithUpdatedTimestampIsNormalizedWithoutStoreCall() throws Exception {
        SteamGame legacy = new SteamGame(10, "Legacy", 0, 0);
        legacy.updateStoreDetail("game", null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, false, false, false);
        setField(legacy, "metadataStatus", null);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of(legacy));

        var result = service.enrichBatch(1);

        assertEquals(1, result.processed());
        assertEquals(EnrichmentStatus.SUCCESS, legacy.getMetadataStatus());
        verifyNoInteractions(store);
        verify(games).save(legacy);
    }

    @Test
    void legacyIgdbWithUpdatedTimestampIsNormalizedWithoutIgdbCall() throws Exception {
        SteamGame legacy = new SteamGame(570, "Legacy IGDB", 0, 0);
        legacy.updateStoreDetail("game", null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, true, false, false);
        legacy.updateIgdb(42L, 1, 10, 10, 5, true, true, false);
        setField(legacy, "igdbStatus", null);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of(legacy));
        when(igdb.configured()).thenReturn(true);

        var result = service.enrichBatch(1);

        assertEquals(1, result.processed());
        assertEquals(EnrichmentStatus.SUCCESS, legacy.getIgdbStatus());
        verify(igdb, never()).findBySteamAppId(anyLong());
        verify(games).save(legacy);
    }

    @Test
    void legacyIgdbWithoutGameIdIsNormalizedAsNotFoundWithoutIgdbCall() throws Exception {
        SteamGame legacy = new SteamGame(4436560, "Legacy no match", 0, 0);
        legacy.updateStoreDetail("game", null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, true, false, false);
        legacy.updateIgdb(null, null, null, null, null, null, null, null);
        setField(legacy, "igdbStatus", null);
        when(games.findMetadataCandidates(any(), any())).thenReturn(List.of());
        when(games.findIgdbCandidates(any())).thenReturn(List.of(legacy));
        when(igdb.configured()).thenReturn(true);

        var result = service.enrichBatch(1);

        assertEquals(1, result.processed());
        assertEquals(EnrichmentStatus.NOT_FOUND, legacy.getIgdbStatus());
        verify(igdb, never()).findBySteamAppId(anyLong());
    }

    @Test
    void completedMetadataAndIgdbAreNotCalledAgainEvenInLimitedInput() {
        SteamGame game = new SteamGame(570, "Dota 2", 0, 0);
        game.updateStoreDetail("game", null, null, true, "KRW", 0, 0, 0, 0,
                "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                false, true, true, false);
        game.updateIgdb(42L, 1, 10, 10, 5, true, true, false);
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        var item = new SteamCatalogClient.CatalogItem(570, "Dota 2", 0, 0);
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null, 1)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 570));
        when(persistence.upsertAll(List.of(item))).thenReturn(List.of(game));
        when(igdb.configured()).thenReturn(true);

        assertEquals(1, service.bootstrapLimited(1));

        verifyNoInteractions(store);
        verify(igdb, never()).findBySteamAppId(anyLong());
    }

    @Test
    void incrementalPageNeverMarksMissingGamesRemoved() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(), false, 0));

        service.sync("steam-catalog");

        verify(games, never()).markMissingAsRemoved(anyString());
    }

    @Test
    void interruptedReconciliationKeepsGenerationAndDoesNotRemoveMissing() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-reconciliation");
        var item = new SteamCatalogClient.CatalogItem(10, "A", 1, 1);
        when(checkpoints.findById("steam-reconciliation")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), true, 10));
        when(persistence.upsertAll(eq(List.of(item)), anyString()))
                .thenReturn(List.of(new SteamGame(10, "A", 1, 1)));

        assertTrue(service.reconcilePage());

        assertEquals(10, checkpoint.getLastAppId());
        assertNotNull(checkpoint.getReconciliationGeneration());
        verify(games, never()).markMissingAsRemoved(anyString());
    }

    @Test
    void onlySuccessfulFinalReconciliationMarksMissingAndClearsGeneration() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-reconciliation");
        String generation = checkpoint.ensureReconciliationGeneration();
        checkpoint.page(10, 0);
        var item = new SteamCatalogClient.CatalogItem(20, "B", 1, 1);
        when(checkpoints.findById("steam-reconciliation")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(10, null)).thenReturn(
                new SteamCatalogClient.CatalogPage(List.of(item), false, 20));
        when(persistence.upsertAll(List.of(item), generation))
                .thenReturn(List.of(new SteamGame(20, "B", 1, 1)));
        when(games.markMissingAsRemoved(generation)).thenReturn(3);

        assertFalse(service.reconcilePage());

        verify(games).markMissingAsRemoved(generation);
        assertNull(checkpoint.getReconciliationGeneration());
        assertEquals("SUCCESS", checkpoint.getStatus());
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        var field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static SteamStoreDetailClient.StoreDetail detail(String type) {
        return new SteamStoreDetailClient.StoreDetail(570, "Game", type, null, null,
                false, "KRW", null, null, null, 0, "NON_ADULT", null, null,
                false, false, Set.of(), Set.of(), true, true, false, false);
    }

    private static SteamStoreDetailClient.StoreDetail detailFor(
            long appId, String name, String type, int price) {
        return new SteamStoreDetailClient.StoreDetail(appId, name, type, null, null,
                price == 0, "KRW", price, price, 0, 0, "NON_ADULT", null, null,
                false, false, Set.of("Action"), Set.of("Single-player"), true,
                false, false, false);
    }

    private record MetadataSnapshot(String name, String type, Integer price,
            EnrichmentStatus status) {}

    private static SteamGame gameWithCurrentMetadata(long appId, String name, String type) {
        SteamGame game = new SteamGame(appId, name, 0, 0);
        game.updateStoreDetail(type, null, null, false, "KRW", null, null,
                null, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, true, false, false);
        return game;
    }
}
