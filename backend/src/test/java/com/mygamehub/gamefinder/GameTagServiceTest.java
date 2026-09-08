package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameTagServiceTest {
    @Test
    void steamOnlyRebuildCreatesNoDuplicateRelationAndDoesNotClaimIgdbCompletion() {
        var tags = mock(GameTagRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var games = mock(SteamGameRepository.class);
        when(tags.findByCanonicalNameIn(anyCollection())).thenReturn(List.of());
        when(tags.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var game = new SteamGame(10, "G", 0, 0);
        game.updateStoreDetail("game", null, null, false, "KRW", 0, 0, 0, 0,
                "NON_ADULT", null, null, false, false, Set.of("Action"),
                Set.of("Co-op"), true, true, false, false);

        var result = new GameTagService(new GameTagTaxonomy(), tags, relations, games)
                .rebuild(game);

        assertEquals(Set.of("action", "coop", "singleplayer", "multiplayer"), result);
        assertEquals(GameTagTaxonomy.STEAM_VERSION, game.getTaxonomyVersion());
        assertEquals(GameTagTaxonomy.STEAM_VERSION, game.getSteamTaxonomyVersion());
        assertNull(game.getIgdbTaxonomyVersion());
        verify(relations).deleteBySteamAppId(10L);
        verify(relations).saveAll(argThat(values -> {
            var list = new ArrayList<SteamGameTag>();
            values.forEach(list::add);
            return list.size() == 4;
        }));
        verify(games).save(game);
    }

    @Test
    void mergedRebuildKeepsSteamAndIgdbTagsWithSourceProvenance() {
        var tags = mock(GameTagRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var games = mock(SteamGameRepository.class);
        var raw = mock(IgdbRawTaxonomyPersistenceService.class);
        when(tags.findByCanonicalNameIn(anyCollection())).thenReturn(List.of());
        when(tags.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var game = new SteamGame(570, "Game", 0, 0);
        game.updateStoreDetail("game", null, null, false, "KRW", 0, 0, 0, 0,
                "NON_ADULT", null, null, false, false, Set.of("Action"), Set.of(),
                true, false, false, false);

        var result = new GameTagService(new GameTagTaxonomy(), tags, relations, games, raw)
                .rebuildWithIgdb(game, List.of(
                        new IgdbTaxonomyValue(IgdbTaxonomySourceType.THEME, 19, "Horror", "horror")));

        assertEquals(Set.of("action", "singleplayer", "horror"), result);
        assertEquals(GameTagTaxonomy.CURRENT_VERSION, game.getTaxonomyVersion());
        assertEquals(GameTagTaxonomy.IGDB_VERSION, game.getIgdbTaxonomyVersion());
        @SuppressWarnings("unchecked")
        var captor = ArgumentCaptor.forClass(Iterable.class);
        verify(relations).saveAll(captor.capture());
        var saved = new ArrayList<SteamGameTag>();
        ((Iterable<SteamGameTag>) captor.getValue()).forEach(saved::add);
        assertTrue(saved.stream().anyMatch(r -> r.getTag().getCanonicalName().equals("horror")
                && r.getSource().equals("IGDB_TAXONOMY")));
        assertTrue(saved.stream().anyMatch(r -> r.getTag().getCanonicalName().equals("action")
                && r.getSource().equals("STEAM_METADATA")));
    }

    @Test
    void igdbNotFoundKeepsSteamTagsAndRecordsTerminalTaxonomyVersion() {
        var tags = mock(GameTagRepository.class);
        var relations = mock(SteamGameTagRepository.class);
        var games = mock(SteamGameRepository.class);
        when(tags.findByCanonicalNameIn(anyCollection())).thenReturn(List.of());
        when(tags.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        var game = new SteamGame(10, "No IGDB match", 0, 0);
        game.updateStoreDetail("game", null, null, false, "KRW", 0, 0, 0, 0,
                "NON_ADULT", null, null, false, false, Set.of("Action"), Set.of(),
                true, false, false, false);
        game.markIgdbNotFound();

        var result = new GameTagService(new GameTagTaxonomy(), tags, relations, games)
                .rebuild(game);

        assertEquals(Set.of("action", "singleplayer"), result);
        assertEquals(GameTagTaxonomy.IGDB_VERSION, game.getIgdbTaxonomyVersion());
        assertEquals(GameTagTaxonomy.CURRENT_VERSION, game.getTaxonomyVersion());
    }
}
