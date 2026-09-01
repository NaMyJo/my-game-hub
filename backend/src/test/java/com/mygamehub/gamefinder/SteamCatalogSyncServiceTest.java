package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.InOrder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SteamCatalogSyncServiceTest {
    private final SteamCatalogClient catalog = mock(SteamCatalogClient.class);
    private final SteamStoreDetailClient store = mock(SteamStoreDetailClient.class);
    private final SteamGameRepository games = mock(SteamGameRepository.class);
    private final CatalogSyncCheckpointRepository checkpoints = mock(CatalogSyncCheckpointRepository.class);
    private final IgdbEnrichmentClient igdb = mock(IgdbEnrichmentClient.class);
    private final SteamCatalogSyncService service =
            new SteamCatalogSyncService(catalog, store, games, checkpoints, igdb, 10, 2);

    @BeforeEach
    void saveAllReturnsItsInput() {
        lenient().when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of());
        lenient().when(games.saveAll(anyCollection()))
                .thenAnswer(invocation -> new ArrayList<>((Collection<SteamGame>) invocation.getArgument(0)));
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
        verify(games).findBySteamAppIdIn(List.of(10L));
        verify(games).saveAll(anyCollection());
        verify(games, never()).findBySteamAppId(anyLong());
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
        SteamGame existing = new SteamGame(30, "Old", 1, 1);
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(30, "New", 300, 3)), false, 30));
        when(games.findBySteamAppIdIn(List.of(30L))).thenReturn(List.of(existing));

        service.sync("steam-catalog");

        verify(games).saveAll(argThat(values -> java.util.stream.StreamSupport
                .stream(values.spliterator(), false).anyMatch(existing::equals)));
        assertEquals("New", existing.getName());
        verify(games, never()).findBySteamAppId(anyLong());
    }

    @Test
    void rowFailureDoesNotAdvancePageCheckpoint() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(40, "Broken", 400, 4)), true, 40));
        when(games.saveAll(anyCollection())).thenThrow(new IllegalStateException("db unavailable"));

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
        SteamCatalogPersistenceService persistence = mock(SteamCatalogPersistenceService.class);
        SteamCatalogSyncService limitedService = new SteamCatalogSyncService(
                catalog, store, games, persistence, checkpoints, igdb,
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
        SteamCatalogPersistenceService persistence = mock(SteamCatalogPersistenceService.class);
        SteamCatalogSyncService diagnosticService = new SteamCatalogSyncService(
                catalog, store, games, persistence, checkpoints, igdb,
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
}
