package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
}
