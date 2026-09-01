package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
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

    @Test
    void paginationPersistsProgressWithoutAdvancingModifiedCheckpoint() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(10, "A", 100, 1)), true, 10));
        when(games.findBySteamAppId(10L)).thenReturn(Optional.empty());

        assertTrue(service.sync("steam-catalog"));

        assertEquals(10, checkpoint.getLastAppId());
        assertNull(checkpoint.getLastModifiedSince());
        assertEquals("RUNNING", checkpoint.getStatus());
        verify(games).save(any(SteamGame.class));
    }

    @Test
    void completedPageAdvancesModifiedCheckpointAndResetsCursor() {
        CatalogSyncCheckpoint checkpoint = new CatalogSyncCheckpoint("steam-catalog");
        when(checkpoints.findById("steam-catalog")).thenReturn(Optional.of(checkpoint));
        when(catalog.page(0, null)).thenReturn(new SteamCatalogClient.CatalogPage(
                List.of(new SteamCatalogClient.CatalogItem(20, "B", 200, 2)), false, 20));
        when(games.findBySteamAppId(20L)).thenReturn(Optional.empty());

        assertFalse(service.sync("steam-catalog"));

        assertEquals(0, checkpoint.getLastAppId());
        assertEquals(200, checkpoint.getLastModifiedSince());
        assertEquals("SUCCESS", checkpoint.getStatus());
        assertNotNull(checkpoint.getLastSuccessfulSyncAt());
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
        when(games.findBySteamAppId(30L)).thenReturn(Optional.of(existing));

        service.sync("steam-catalog");

        verify(games).save(existing);
        assertEquals("New", existing.getName());
        verify(games, never()).save(argThat(game -> game != existing));
    }
}
