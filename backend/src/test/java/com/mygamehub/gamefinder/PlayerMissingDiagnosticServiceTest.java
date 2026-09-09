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
        assertThat(response.games().getFirst().recoveryEvidence())
                .isEqualTo("MULTIPLAYER_EVIDENCE_WITHOUT_NUMERIC_CAPACITY");
        verify(games, never()).save(any());
        verify(games, never()).saveAll(any());
    }

    @Test
    void capacityDiagnosticAliasesRemainReadOnlyAndBounded() {
        var games = mock(SteamGameRepository.class);
        when(games.findPlayerMissingSamples(eq("MULTIPLAYER_CANDIDATE"),
                any(Pageable.class))).thenReturn(List.of());
        var service = new PlayerMissingDiagnosticService(games);

        assertThat(service.samples(
                PlayerMissingDiagnosticService.Classification.CAPACITY_UNKNOWN, 500).limit())
                .isEqualTo(50);
        assertThat(service.samples(
                PlayerMissingDiagnosticService.Classification.RECOVERABLE, 20).games()).isEmpty();
        assertThat(service.samples(
                PlayerMissingDiagnosticService.Classification.INSUFFICIENT, 20).games()).isEmpty();
        verify(games, times(1)).findPlayerMissingSamples(eq("MULTIPLAYER_CANDIDATE"),
                any(Pageable.class));
        verify(games, never()).save(any());
    }
}
