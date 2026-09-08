package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameTaxonomyRebuildServiceTest {
    @Test
    void rebuildsExistingTaggedGamesByVersionInDeterministicBatch() {
        Fixture f = new Fixture();
        SteamGame first = new SteamGame(10, "A", 0, 0);
        SteamGame second = new SteamGame(20, "B", 0, 0);
        when(f.games.findTaxonomyVersionCandidates(eq(GameTagTaxonomy.STEAM_VERSION), any()))
                .thenReturn(List.of(first, second));

        var result = f.service.rebuildBatch(200);

        verify(f.tags).rebuild(first);
        verify(f.tags).rebuild(second);
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(f.games).findTaxonomyVersionCandidates(eq(GameTagTaxonomy.STEAM_VERSION), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(200);
        assertThat(result.lastAppId()).isEqualTo(20);
        assertThat(result.completed()).isTrue();
        verify(f.checkpoints).save(any(CatalogSyncCheckpoint.class));
    }

    @Test
    void latestVersionIsSkippedAndRepeatedRunIsIdempotent() {
        Fixture f = new Fixture();
        when(f.games.findTaxonomyVersionCandidates(anyString(), any())).thenReturn(List.of());
        assertThat(f.service.rebuildBatch(200).processed()).isZero();
        assertThat(f.service.rebuildBatch(200).processed()).isZero();
        verifyNoInteractions(f.tags);
    }

    @Test
    void batchSizeIsBoundedBetweenOneHundredAndFiveHundred() {
        Fixture f = new Fixture();
        when(f.games.findTaxonomyVersionCandidates(anyString(), any())).thenReturn(List.of());
        f.service.rebuildBatch(1);
        f.service.rebuildBatch(1000);
        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(f.games, times(2)).findTaxonomyVersionCandidates(anyString(), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getPageSize)
                .containsExactly(100, 500);
    }

    @Test
    void failureDoesNotAdvanceOrSaveCheckpoint() {
        Fixture f = new Fixture();
        SteamGame first = new SteamGame(10, "A", 0, 0);
        when(f.games.findTaxonomyVersionCandidates(anyString(), any())).thenReturn(List.of(first));
        doThrow(new IllegalStateException("tag write failed")).when(f.tags).rebuild(first);

        assertThrows(IllegalStateException.class, () -> f.service.rebuildBatch(200));
        verify(f.checkpoints, never()).save(any());
    }

    private static class Fixture {
        final SteamGameRepository games = mock(SteamGameRepository.class);
        final GameTagService tags = mock(GameTagService.class);
        final CatalogSyncCheckpointRepository checkpoints = mock(CatalogSyncCheckpointRepository.class);
        final GameTaxonomyRebuildService service = new GameTaxonomyRebuildService(games, tags, checkpoints);
        Fixture() { when(checkpoints.findById(anyString())).thenReturn(Optional.empty()); }
    }
}
