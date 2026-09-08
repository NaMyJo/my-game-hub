package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderTagSearchRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameFinderTagSearchServiceTest {
    @Test
    void hardFiltersAndPaginationAreAppliedByBoundedDatabaseQuery() {
        var games = mock(SteamGameRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var first = game(41, 8000, 1, 4);
        var second = game(42, 9000, 2, 5);
        when(relations.findFilteredAppIdsMatchingAll(anyCollection(), eq(2L),
                eq(0), eq(10000), eq(false), eq(false), eq(3), eq(5),
                eq(false), any(Pageable.class)))
                .thenReturn(List.of(41L, 42L));
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of(second, first));

        var request = new GameFinderTagSearchRequest("협동 생존", List.of(),
                0, 10000, false, 3, 5, 2, 20);
        var result = new GameFinderTagSearchService(games, relations, new GameTagTaxonomy())
                .searchPage(request);

        assertEquals(List.of(41L, 42L), result.items().stream().map(v -> v.steamAppId()).toList());
        var pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(relations).findFilteredAppIdsMatchingAll(
                argThat(tags -> tags.containsAll(Set.of("coop", "survival"))), eq(2L),
                eq(0), eq(10000), eq(false), eq(false), eq(3), eq(5),
                eq(false), pageable.capture());
        assertEquals(2, pageable.getValue().getPageNumber());
        assertEquals(21, pageable.getValue().getPageSize());
        verify(games).findBySteamAppIdIn(argThat(ids -> ids.size() <= 21));
        verifyNoMoreInteractions(games);
    }

    @Test
    void emptyTagQueryDoesNotLoadCatalog() {
        var games = mock(SteamGameRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var request = new GameFinderTagSearchRequest("", List.of(),
                0, 100000, false, 1, 15, 0, 100);

        var result = new GameFinderTagSearchService(games, relations, new GameTagTaxonomy())
                .searchPage(request);

        assertTrue(result.items().isEmpty());
        verifyNoInteractions(games, relations);
    }

    private SteamGame game(long id, Integer price, Integer min, Integer max) {
        var game = new SteamGame(id, "G" + id, 0, 0);
        game.updateStoreDetail("game", null, null, false, "KRW", price, price,
                0, 0, "NON_ADULT", null, null, false, false, Set.of(), Set.of(),
                true, true, false, false);
        if (min != null) game.updateIgdb(id, min, max, max, null, true, false, false);
        return game;
    }
}
