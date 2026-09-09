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
        var syncService = mock(SteamCatalogSyncService.class);
        var projection = mock(GameFinderAdminStatusProjection.class);
        var classification = mock(PlayerMissingClassificationProjection.class);
        var fullCheckpoint = new CatalogSyncCheckpoint("steam-catalog-admin-full-sync");
        fullCheckpoint.fullSyncPage(500, 500, false);
        when(checkpoints.findById("steam-catalog-admin-full-sync"))
                .thenReturn(java.util.Optional.of(fullCheckpoint));
        when(games.adminStatus()).thenReturn(projection);
        when(games.playerMissingClassification()).thenReturn(classification);
        when(projection.getTotal()).thenReturn(102L);
        when(projection.getActive()).thenReturn(100L);
        when(projection.getUnavailable()).thenReturn(1L);
        when(projection.getRemoved()).thenReturn(1L);
        when(projection.getGameCatalogCount()).thenReturn(90L);
        when(projection.getMetadataPending()).thenReturn(79L);
        when(projection.getMetadataSuccess()).thenReturn(23L);
        when(projection.getIgdbPending()).thenReturn(81L);
        when(projection.getIgdbSuccess()).thenReturn(21L);
        when(projection.getGameCount()).thenReturn(80L);
        when(projection.getNonGameCount()).thenReturn(10L);
        when(projection.getUnclassifiedCount()).thenReturn(12L);
        when(projection.getPlayerDataCount()).thenReturn(25L);
        when(projection.getPlayerDataMissingCount()).thenReturn(55L);
        when(projection.getIgdbSuccessCount()).thenReturn(20L);
        when(projection.getIgdbSuccessPlayerDataCount()).thenReturn(8L);
        when(projection.getIgdbSuccessPlayerDataMissingCount()).thenReturn(12L);
        when(classification.getPlayerDataMissingTotal()).thenReturn(55L);
        when(classification.getMultiplayerCandidateCount()).thenReturn(20L);
        when(classification.getSingleplayerOnlyCandidateCount()).thenReturn(25L);
        when(classification.getUnknownCount()).thenReturn(10L);
        when(classification.getIgdbSuccessPlayerDataMissingTotal()).thenReturn(12L);
        when(classification.getIgdbSuccessMultiplayerCandidateCount()).thenReturn(5L);
        when(classification.getIgdbSuccessSingleplayerOnlyCandidateCount()).thenReturn(4L);
        when(classification.getIgdbSuccessUnknownCount()).thenReturn(3L);
        when(syncService.remainingEnrichmentCandidates()).thenReturn(17L);

        var response = new GameFinderAdminStatusService(
                games, checkpoints, syncService).status();

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
        assertThat(response.remainingCandidates()).isEqualTo(17);
        assertThat(response.gameCatalogCount()).isEqualTo(90);
        assertThat(response.gameCount()).isEqualTo(80);
        assertThat(response.nonGameCount()).isEqualTo(10);
        assertThat(response.unclassifiedCount()).isEqualTo(12);
        assertThat(response.playerDataCount()).isEqualTo(25);
        assertThat(response.playerDataMissingCount()).isEqualTo(55);
        assertThat(response.igdbSuccessCount()).isEqualTo(20);
        assertThat(response.igdbSuccessPlayerDataCount()).isEqualTo(8);
        assertThat(response.igdbSuccessPlayerDataMissingCount()).isEqualTo(12);
        assertThat(response.playerMissingClassification().multiplayerCandidateCount()).isEqualTo(20);
        assertThat(response.playerMissingClassification().singleplayerOnlyCandidateCount()).isEqualTo(25);
        assertThat(response.playerMissingClassification().unknownCount()).isEqualTo(10);
    }
}
