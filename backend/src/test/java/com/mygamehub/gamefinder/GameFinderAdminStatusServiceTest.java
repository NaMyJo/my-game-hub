package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameFinderAdminStatusServiceTest {
    @Test
    void mapsSingleAggregateProjectionWithoutLoadingEntities() {
        var games = mock(SteamGameRepository.class);
        var projection = mock(GameFinderAdminStatusProjection.class);
        when(games.adminStatus()).thenReturn(projection);
        when(projection.getTotal()).thenReturn(102L);
        when(projection.getActive()).thenReturn(100L);
        when(projection.getUnavailable()).thenReturn(1L);
        when(projection.getRemoved()).thenReturn(1L);
        when(projection.getMetadataPending()).thenReturn(79L);
        when(projection.getMetadataSuccess()).thenReturn(23L);
        when(projection.getIgdbPending()).thenReturn(81L);
        when(projection.getIgdbSuccess()).thenReturn(21L);

        var response = new GameFinderAdminStatusService(games).status();

        assertThat(response.total()).isEqualTo(102);
        assertThat(response.active()).isEqualTo(100);
        assertThat(response.metadata().pending()).isEqualTo(79);
        assertThat(response.metadata().success()).isEqualTo(23);
        assertThat(response.igdb().pending()).isEqualTo(81);
        assertThat(response.igdb().success()).isEqualTo(21);
    }
}
