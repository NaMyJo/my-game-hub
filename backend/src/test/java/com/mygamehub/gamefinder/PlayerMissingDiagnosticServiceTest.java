package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PlayerMissingDiagnosticServiceTest {
    @Test
    void returnsBoundedProjectionSamplesWithoutWriting() {
        var games = mock(SteamGameRepository.class);
        var row = mock(PlayerMissingSampleProjection.class);
        when(row.getSteamAppId()).thenReturn(570L);
        when(row.getName()).thenReturn("Dota 2");
        when(row.getIgdbGameId()).thenReturn(2963L);
        when(row.getCanonicalTags()).thenReturn("moba|multiplayer");
        when(row.getIgdbGameModes()).thenReturn("multiplayer|co-operative");
        when(games.findPlayerMissingSamples(eq("MULTIPLAYER_CANDIDATE"), any(Pageable.class)))
                .thenReturn(List.of(row));

        var response = new PlayerMissingDiagnosticService(games).samples(
                PlayerMissingDiagnosticService.Classification.MULTIPLAYER_CANDIDATE, 20);

        assertThat(response.games()).hasSize(1);
        assertThat(response.games().getFirst().canonicalTags())
                .containsExactly("moba", "multiplayer");
        assertThat(response.games().getFirst().igdbGameModes())
                .containsExactly("multiplayer", "co-operative");
        verify(games, never()).save(any());
        verify(games, never()).saveAll(any());
    }
}
