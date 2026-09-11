package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderRecommendRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class GameFinderRecommendationServiceTest {
    @Test
    void hardFiltersArePassedToBoundedDatabaseProjectionQuery() {
        Fixture fixture = new Fixture();
        var request = request(List.of(), List.of("action"), 0, 10000, false, 2, 4, List.of());
        fixture.candidates(List.of(candidate(2, "pass", 5000)));
        fixture.tag(2, "action");

        var result = fixture.service.recommend(request);

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(2L);
        verify(fixture.relations).findRankedRecommendationAppIds(anyCollection(), eq(0),
                eq(10000), eq(false), eq(false), eq(2), eq(4), eq(false),
                eq(true), anyLong(),
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
    void databaseRankedCandidatePoolIsDeduplicatedAndNeverExceedsTwoThousand() {
        Fixture fixture = new Fixture();
        var candidates = new ArrayList<GameFinderRecommendationCandidate>();
        for (int i = 1; i <= 2100; i++) {
            candidates.add(candidate(i, "candidate-" + i, 0));
            fixture.tag(i, "action");
        }
        fixture.candidates(candidates);

        fixture.service.recommend(request(List.of(), List.of("action"),
                0, 100000, true, 1, 15, List.of()));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(fixture.relations).findCanonicalNamesBySteamAppIds(ids.capture());
        assertThat(ids.getValue()).hasSize(2000).doesNotHaveDuplicates();
        verify(fixture.games, never()).findAll();
        verify(fixture.relations).findRankedRecommendationAppIds(anyCollection(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyLong(),
                argThat(page -> page.getPageSize() == GameFinderRecommendationService.MAX_CANDIDATE_POOL));
    }

    @Test
    void anyReleasePreferenceAddsNoRecencyOrdering() {
        Fixture fixture = new Fixture();
        fixture.candidates(List.of(
                candidate(1, "old", 0, LocalDate.now().minusYears(12)),
                candidate(2, "new", 0, LocalDate.now().minusMonths(3))));
        fixture.tag(1, "action");
        fixture.tag(2, "action");

        var result = fixture.service.recommend(request(List.of(), List.of("action"),
                0, 100000, false, 1, 15, List.of(), ReleasePreference.ANY));

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(1L, 2L);
        verify(fixture.relations).findRankedRecommendationAppIds(anyCollection(), anyInt(),
                anyInt(), anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyBoolean(),
                eq(false), anyLong(), any());
    }

    @Test
    void balancedUsesWeakerReleaseBoostThanRecent() {
        Fixture recentFixture = new Fixture();
        Fixture balancedFixture = new Fixture();
        var fresh = candidate(2, "fresh", 0, LocalDate.now().minusMonths(3));
        recentFixture.candidates(List.of(fresh));
        balancedFixture.candidates(List.of(fresh));
        recentFixture.tag(2, "action");
        balancedFixture.tag(2, "action");

        int recentScore = recentFixture.service.recommend(request(List.of(),
                List.of("action", "rpg"), 0, 100000, false, 1, 15, List.of(),
                ReleasePreference.RECENT)).getFirst().matchScore();
        int balancedScore = balancedFixture.service.recommend(request(List.of(),
                List.of("action", "rpg"), 0, 100000, false, 1, 15, List.of(),
                ReleasePreference.BALANCED)).getFirst().matchScore();

        assertThat(recentScore).isGreaterThan(balancedScore);
        verify(balancedFixture.relations).findRankedRecommendationAppIds(anyCollection(),
                anyInt(), anyInt(), anyBoolean(), anyBoolean(), anyInt(), anyInt(),
                anyBoolean(), eq(false), anyLong(), any());
    }

    @Test
    void diagnosticYearDistributionSeparatesMissingAndFutureReleaseDates() {
        Fixture fixture = new Fixture();
        var distribution = fixture.service.candidateYearDistribution(List.of(
                candidate(1, "2026", 0, LocalDate.of(2026, 1, 1)),
                candidate(2, "2025", 0, LocalDate.of(2025, 1, 1)),
                candidate(3, "2024", 0, LocalDate.of(2024, 1, 1)),
                candidate(4, "modern", 0, LocalDate.of(2022, 1, 1)),
                candidate(5, "old", 0, LocalDate.of(2015, 1, 1)),
                candidate(6, "classic", 0, LocalDate.of(2008, 1, 1)),
                candidate(7, "future", 0, LocalDate.now().plusYears(2)),
                candidate(8, "missing", 0, null)));

        assertThat(distribution).containsEntry("2026", 1L)
                .containsEntry("2025", 1L)
                .containsEntry("2024", 1L)
                .containsEntry("2020-2023", 1L)
                .containsEntry("2010-2019", 1L)
                .containsEntry("2009_OR_EARLIER", 1L)
                .containsEntry("FUTURE", 1L)
                .containsEntry("NULL", 1L);
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
    void highAppIdRelevantGameIsNotCutOffByLowAppIdCandidateWindow() {
        Fixture fixture = new Fixture();
        var relevant = candidate(900_000, "high-id-relevant", 0);
        fixture.candidates(List.of(relevant));
        fixture.tag(900_000, "rpg");

        var result = fixture.service.recommend(request(
                List.of(), List.of("rpg"), 0, 100000, false, 1, 15, List.of()));

        assertThat(result).extracting(value -> value.steamAppId()).contains(900_000L);
        verify(fixture.relations).findRankedRecommendationAppIds(
                argThat(tags -> tags.contains("rpg")), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                anyLong(), any());
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
    void seedOnlyUsesSeedTagsForRelevantCandidateRetrieval() {
        Fixture fixture = new Fixture();
        when(fixture.games.findBySteamAppIdIn(List.of(1L))).thenReturn(List.of(game(1, "liked", "action")));
        fixture.candidates(List.of(candidate(2, "similar", 0), candidate(3, "other", 0)));
        fixture.tag(1, "action");
        fixture.tag(2, "action");
        fixture.tag(3, "rpg");

        var result = fixture.service.recommend(request(
                List.of(1L), List.of(), 0, 100000, false, 1, 15, List.of()));

        assertThat(result).extracting(value -> value.steamAppId()).contains(2L);
        verify(fixture.relations).findRankedRecommendationAppIds(
                argThat(tags -> tags.contains("action")), anyInt(), anyInt(), anyBoolean(),
                anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                anyLong(), any());
    }

    @Test
    void recentPreferenceSoftBoostsNewerGameWhenRelevanceIsEqual() {
        Fixture fixture = new Fixture();
        fixture.candidates(List.of(
                candidate(1, "old", 0, LocalDate.now().minusYears(12)),
                candidate(2, "new", 0, LocalDate.now().minusMonths(3))));
        fixture.tag(1, "action");
        fixture.tag(2, "action");

        var result = fixture.service.recommend(request(List.of(), List.of("action"),
                0, 100000, false, 1, 15, List.of(), ReleasePreference.RECENT));

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(2L, 1L);
    }

    @Test
    void recentPreferenceOrdersByReleaseDateBeforeTagRelevance() {
        Fixture fixture = new Fixture();
        fixture.candidates(List.of(
                candidate(1, "old-match", 0, LocalDate.now().minusYears(12)),
                candidate(2, "new-partial", 0, LocalDate.now().minusMonths(3))));
        fixture.tag(1, "action", "rpg");
        fixture.tag(2, "action");

        var result = fixture.service.recommend(request(List.of(), List.of("action", "rpg"),
                0, 100000, false, 1, 15, List.of(), ReleasePreference.RECENT));

        assertThat(result).extracting(value -> value.steamAppId()).containsExactly(2L, 1L);
    }

    @Test
    void noSeedsAndNoTagsIsRejectedWithoutCandidateQuery() {
        Fixture fixture = new Fixture();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> fixture.service.recommend(
                request(List.of(), List.of(), 0, 100000, false, 1, 15, List.of()))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(fixture.games, never()).findRecommendationCandidatesByAppIds(anyCollection());
    }

    private static GameFinderRecommendRequest request(List<Long> liked, List<String> tags,
            int minPrice, int maxPrice, boolean adult, int minPlayers, int maxPlayers,
            List<Long> excluded) {
        return new GameFinderRecommendRequest(liked, tags, minPrice, maxPrice, adult,
                minPlayers, maxPlayers, excluded);
    }

    private static GameFinderRecommendRequest request(List<Long> liked, List<String> tags,
            int minPrice, int maxPrice, boolean adult, int minPlayers, int maxPlayers,
            List<Long> excluded, ReleasePreference releasePreference) {
        return new GameFinderRecommendRequest(liked, tags, minPrice, maxPrice, adult,
                minPlayers, maxPlayers, releasePreference, excluded);
    }

    private static GameFinderRecommendationCandidate candidate(long id, String name, int price) {
        return candidate(id, name, price, null);
    }

    private static GameFinderRecommendationCandidate candidate(long id, String name, int price,
            LocalDate releaseDate) {
        return new GameFinderRecommendationCandidate(id, name, null, price, price, 0,
                "KRW", price == 0, releaseDate, null, false, true, false, false, 1, "action");
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
            Map<Long, GameFinderRecommendationCandidate> byId = values.stream().collect(
                    java.util.stream.Collectors.toMap(GameFinderRecommendationCandidate::steamAppId,
                            value -> value, (left, right) -> left));
            when(relations.findRankedRecommendationAppIds(anyCollection(), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyInt(), anyInt(), anyBoolean(), anyBoolean(),
                    anyLong(), any(Pageable.class))).thenAnswer(invocation -> {
                        Pageable page = invocation.getArgument(10);
                        return values.stream().map(GameFinderRecommendationCandidate::steamAppId)
                                .distinct().limit(page.getPageSize()).toList();
                    });
            when(games.findRecommendationCandidatesByAppIds(anyCollection())).thenAnswer(invocation -> {
                Collection<Long> ids = invocation.getArgument(0);
                return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
            });
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
