package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GameFinderCatalogQueryServiceTest {
    @Test
    void detailKeepsRemovedGameAvailableForExistingReferences() {
        var games = mock(SteamGameRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var tags = mock(GameTagRepository.class);
        var removed = new SteamGame(10, "Removed", 0, 0);
        removed.markRemoved();
        when(games.findBySteamAppId(10L)).thenReturn(Optional.of(removed));
        when(relations.findCanonicalNamesBySteamAppId(10L)).thenReturn(List.of("coop"));

        var result = new GameFinderCatalogQueryService(games, relations, tags, new GameTagTaxonomy()).detail(10L).orElseThrow();

        assertThat(result.lifecycleStatus()).isEqualTo("REMOVED");
        assertThat(result.canonicalTags()).containsExactly("coop");
    }

    @Test
    void tagAutocompleteUsesBackendCanonicalDataAndSupportsPagination() {
        var games = mock(SteamGameRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var tags = mock(GameTagRepository.class);
        when(tags.autocomplete(eq("coop"), any(Pageable.class))).thenReturn(List.of(
                new GameTag("coop", "협동", "FEATURE"),
                new GameTag("online-coop", "온라인 협동", "FEATURE")));

        var result = new GameFinderCatalogQueryService(games, relations, tags, new GameTagTaxonomy())
                .autocomplete("코옵", 0, 1);

        assertThat(result.items()).extracting(value -> value.canonicalName()).containsExactly("coop");
        assertThat(result.hasNext()).isTrue();
    }
}
