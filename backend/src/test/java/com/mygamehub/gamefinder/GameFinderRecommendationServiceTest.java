package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderRecommendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameFinderRecommendationServiceTest {
    @Test
    void hardFiltersArePassedToBoundedDatabaseProjectionQuery() {
        Fixture fixture = new Fixture();
        var request = request(List.of(), List.of("action"), 0, 10000, false, 2, 4, List.of());
        when(fixture.games.findRecommendationCandidates(anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), any()))
                .thenReturn(List.of(candidate(2, "pass", 5000)));
        fixture.tag(2, "action");

        var result = fixture.service.recommend(request);

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(2L);
        verify(fixture.games).findRecommendationCandidates(eq(0), eq(10000), eq(false),
                eq(false), eq(2), eq(4), eq(false), eq(false),
                argThat(page -> page.getPageSize() == GameFinderRecommendationService.MAX_CANDIDATE_POOL));
    }

    @Test
    void returnsAtMostTwentyFromBoundedCandidatePool() {
        Fixture fixture = new Fixture();
        var candidates = new ArrayList<GameFinderRecommendationCandidate>();
        for (int i = 2; i < 40; i++) {
            candidates.add(candidate(i, "g" + i, 0));
            fixture.tag(i, "action");
        }
        fixture.candidates(candidates);
        assertThat(fixture.service.recommend(request(List.of(), List.of("action"),
                0, 100000, true, 1, 15, List.of()))).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void recommendationPagesAreDeterministicAndDoNotOverlap() {
        Fixture fixture = new Fixture();
        var candidates = new ArrayList<GameFinderRecommendationCandidate>();
        for (int i = 2; i < 32; i++) {
            candidates.add(candidate(i, "g" + i, 0));
            fixture.tag(i, "action");
        }
        fixture.candidates(candidates);
        var request = request(List.of(), List.of("action"), 0, 100000, true, 1, 15, List.of());

        var first = fixture.service.recommendPage(request, 0, 5);
        var repeated = fixture.service.recommendPage(request, 0, 5);
        var second = fixture.service.recommendPage(request, 1, 5);

        assertThat(first.items()).isEqualTo(repeated.items());
        assertThat(first.items()).extracting(value -> value.steamAppId())
                .doesNotContainAnyElementsOf(second.items().stream().map(value -> value.steamAppId()).toList());
    }

    @Test
    void tagsOnlyRanksMatchesAheadWithoutHardFilteringOthers() {
        Fixture fixture = new Fixture();
        fixture.candidates(List.of(candidate(2, "action", 0), candidate(1, "rpg", 0)));
        fixture.tag(1, "rpg");
        fixture.tag(2, "action");

        var result = fixture.service.recommend(request(
                List.of(), List.of("RPG"), 0, 100000, false, 1, 15, List.of()));

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(1L, 2L);
    }

    @Test
    void seedAndPreferredTagsUseCanonicalRelations() {
        Fixture fixture = new Fixture();
        SteamGame liked = game(1, "liked", "action");
        when(fixture.games.findBySteamAppIdIn(List.of(1L))).thenReturn(List.of(liked));
        fixture.candidates(List.of(candidate(2, "plain", 0), candidate(3, "coop", 0)));
        fixture.tag(1, "action");
        fixture.tag(2, "action", "rpg", "strategy", "simulation");
        fixture.tag(3, "action", "coop");

        var result = fixture.service.recommend(request(
                List.of(1L), List.of("coop"), 0, 100000, false, 1, 15, List.of()));

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(3L, 2L);
    }

    @Test
    void noSeedsAndNoTagsIsRejectedWithoutCandidateQuery() {
        Fixture fixture = new Fixture();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> fixture.service.recommend(
                request(List.of(), List.of(), 0, 100000, false, 1, 15, List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(fixture.games, never()).findRecommendationCandidates(anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyBoolean(), any());
    }

    private static GameFinderRecommendRequest request(List<Long> liked, List<String> tags,
            int minPrice, int maxPrice, boolean adult, int minPlayers, int maxPlayers,
            List<Long> excluded) {
        return new GameFinderRecommendRequest(liked, tags, minPrice, maxPrice, adult,
                minPlayers, maxPlayers, excluded);
    }

    private static GameFinderRecommendationCandidate candidate(long id, String name, int price) {
        return new GameFinderRecommendationCandidate(id, name, null, price, price, 0,
                "KRW", price == 0, null, null, false, true, false, false, 1, "action");
    }

    private static SteamGame game(long id, String name, String... genres) {
        var game = new SteamGame(id, name, 1, 1);
        game.updateStoreDetail("game", null, null, true, "KRW", 0, 0, 0, 0,
                "NON_ADULT", null, null, false, false,
                new LinkedHashSet<>(List.of(genres)), Set.of(), true, false, false, false);
        return game;
    }

    private static class Fixture {
        final SteamGameRepository games = mock(SteamGameRepository.class);
        final SteamGameTagRepository relations = mock(SteamGameTagRepository.class);
        final List<SteamGameTagValue> tagValues = new ArrayList<>();
        final GameFinderRecommendationService service = new GameFinderRecommendationService(
                games, relations, new GameTagTaxonomy());

        Fixture() {
            when(relations.findCanonicalNamesBySteamAppIds(anyCollection()))
                    .thenAnswer(invocation -> {
                        Collection<Long> ids = invocation.getArgument(0);
                        return tagValues.stream().filter(value -> ids.contains(value.getSteamAppId())).toList();
                    });
        }

        void candidates(List<GameFinderRecommendationCandidate> values) {
            when(games.findRecommendationCandidates(anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                    anyInt(), anyInt(), anyBoolean(), anyBoolean(), any(Pageable.class)))
                    .thenReturn(values);
        }

        void tag(long appId, String... names) {
            for (String name : names) tagValues.add(tagValue(appId, name));
        }
    }

    private static SteamGameTagValue tagValue(long appId, String canonical) {
        return new SteamGameTagValue() {
            @Override public Long getSteamAppId() { return appId; }
            @Override public String getCanonicalName() { return canonical; }
        };
    }
}
