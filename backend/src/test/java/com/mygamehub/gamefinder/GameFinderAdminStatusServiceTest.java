package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameFinderAdminStatusServiceTest {
    @Test
    void mapsSingleAggregateProjectionWithoutLoadingEntities() {
        var games = mock(SteamGameRepository.class);
        var checkpoints = mock(CatalogSyncCheckpointRepository.class);
        var projection = mock(GameFinderAdminStatusProjection.class);
        var fullCheckpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        fullCheckpoint.fullSyncPage(500, 500, false);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(java.util.Optional.of(fullCheckpoint));
        when(games.adminStatus()).thenReturn(projection);
        when(projection.getTotal()).thenReturn(102L);
        when(projection.getActive()).thenReturn(100L);
        when(projection.getUnavailable()).thenReturn(1L);
        when(projection.getRemoved()).thenReturn(1L);
        when(projection.getMetadataPending()).thenReturn(79L);
        when(projection.getMetadataSuccess()).thenReturn(23L);
        when(projection.getIgdbPending()).thenReturn(81L);
        when(projection.getIgdbSuccess()).thenReturn(21L);

        var response = new GameFinderAdminStatusService(games, checkpoints).status();

        assertThat(response.total()).isEqualTo(102);
        assertThat(response.active()).isEqualTo(100);
        assertThat(response.metadata().pending()).isEqualTo(79);
        assertThat(response.metadata().success()).isEqualTo(23);
        assertThat(response.igdb().pending()).isEqualTo(81);
        assertThat(response.igdb().success()).isEqualTo(21);
        assertThat(response.checkpoint().status()).isEqualTo("NEW");
        assertThat(response.fullCatalogSync().lastAppId()).isEqualTo(500);
        assertThat(response.fullCatalogSync().discoveredCount()).isEqualTo(500);
        assertThat(response.fullCatalogSync().completed()).isFalse();
    }
}
