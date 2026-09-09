package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import jakarta.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class GameFinderRecommendationService {
    private static final Logger log = LoggerFactory.getLogger(GameFinderRecommendationService.class);
    static final String CANDIDATE_STRATEGY = "full-catalog-ranked-v2";
    static final int MAX_CANDIDATE_POOL = 2_000;
    private final SteamGameRepository repository;
    private final SteamGameTagRepository relations;
    private final GameTagTaxonomy taxonomy;
    @Value("${app.game-finder.recommendation-diagnostics-enabled:false}")
    private boolean diagnosticsEnabled;

    public GameFinderRecommendationService(SteamGameRepository repository,
            SteamGameTagRepository relations, GameTagTaxonomy taxonomy) {
        this.repository = repository;
        this.relations = relations;
        this.taxonomy = taxonomy;
    }

    @PostConstruct
    void logCandidateStrategy() {
        log.info("game_finder_recommendation_strategy strategy={} candidateLimit={}",
                CANDIDATE_STRATEGY, MAX_CANDIDATE_POOL);
    }

    public List<GameFinderSearchResponse> search(String query) {
        return searchPage(query, 0, 20).items();
    }

    public GameFinderPageResponse<GameFinderSearchResponse> searchPage(String query, int page, int size) {
        if (query == null || query.trim().length() < 2) {
            return new GameFinderPageResponse<>(List.of(), page, size, false);
        }
        var values = repository.findActiveByName(query.trim(), PageRequest.of(page, size + 1))
                .stream().map(g -> new GameFinderSearchResponse(
                        g.getSteamAppId(), g.getName(), g.getHeaderImageUrl()))
                .toList();
        return GameFinderPageResponse.from(values, page, size);
    }

    public List<GameFinderRecommendationResponse> recommend(GameFinderRecommendRequest request) {
        return recommendPage(request, 0, 20).items();
    }

    public GameFinderPageResponse<GameFinderRecommendationResponse> recommendPage(
            GameFinderRecommendRequest request, int page, int size) {
        if (request.priceMin() > request.priceMax() || request.playerMin() > request.playerMax()) {
            throw new IllegalArgumentException("최소 범위는 최대 범위보다 클 수 없습니다.");
        }
        Set<String> preferred = normalizePreferredTags(request.preferredTags());
        if (request.likedSteamAppIds().isEmpty() && preferred.isEmpty()) {
            throw new IllegalArgumentException("취향 게임 또는 선호 태그를 하나 이상 선택해주세요.");
        }

        List<SteamGame> liked = repository.findBySteamAppIdIn(request.likedSteamAppIds());
        if (liked.size() != new HashSet<>(request.likedSteamAppIds()).size()) {
            throw new IllegalArgumentException("선택한 Steam 게임 정보를 찾을 수 없습니다.");
        }
        Map<Long, Set<String>> seedTags = tagsByAppIds(request.likedSteamAppIds());
        Set<String> taste = seedTags.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (taste.isEmpty()) liked.forEach(game -> taste.addAll(taxonomy.fromSteam(game)));

        boolean priceUnrestricted = request.priceMin() == 0 && request.priceMax() == 100000;
        boolean playersUnrestricted = request.playerMin() == 1 && request.playerMax() == 15;
        Set<String> retrievalTags = new LinkedHashSet<>(taste);
        retrievalTags.addAll(preferred);
        if (diagnosticsEnabled) {
            logRequestDiagnostics(request, preferred, seedTags, retrievalTags);
        }
        List<GameFinderRecommendationCandidate> candidates = candidates(request, retrievalTags,
                priceUnrestricted, playersUnrestricted);
        Map<Long, Set<String>> candidateTags = tagsByAppIds(
                candidates.stream().map(GameFinderRecommendationCandidate::steamAppId).toList());

        Set<Long> excluded = new HashSet<>(request.likedSteamAppIds());
        if (request.excludeAppIds() != null) excluded.addAll(request.excludeAppIds());
        List<Scored> scored = candidates.stream()
                .filter(candidate -> !excluded.contains(candidate.steamAppId()))
                .map(candidate -> new Scored(candidate,
                        score(taste, preferred, candidateTags.getOrDefault(
                                candidate.steamAppId(), Set.of()), candidate.releaseDate(),
                                request.releasePreference()),
                        candidateTags.getOrDefault(candidate.steamAppId(), Set.of())))
                .filter(value -> request.likedSteamAppIds().isEmpty() || value.score >= 0.08)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingLong(value -> value.game().steamAppId()))
                .toList();

        int offset = page * size;
        List<Scored> diversified = diversify(scored);
        if (diagnosticsEnabled) {
            logCandidateDiagnostics(candidates, candidateTags, retrievalTags);
            logFinalDiagnostics(scored, diversified, taste, preferred,
                    ReleasePreference.defaultIfNull(request.releasePreference()));
        }
        List<GameFinderRecommendationResponse> pageValues = diversified.stream()
                .skip(offset).limit(size + 1L).map(this::response).toList();
        return GameFinderPageResponse.from(pageValues, page, size);
    }

    private List<GameFinderRecommendationCandidate> candidates(GameFinderRecommendRequest request,
            Set<String> retrievalTags, boolean priceUnrestricted, boolean playersUnrestricted) {
        Set<String> queryTags = retrievalTags.isEmpty()
                ? Set.of("__game_finder_no_matching_tag__") : retrievalTags;
        long tieSeed = candidateTieSeed(request, retrievalTags);
        boolean preferRecent = ReleasePreference.defaultIfNull(request.releasePreference())
                == ReleasePreference.RECENT;
        if (diagnosticsEnabled) {
            log.info("game_finder_recommendation_repository strategy={} method=findRankedRecommendationAppIds "
                            + "preferRecent={} candidateLimit={} tieSeed={}",
                    CANDIDATE_STRATEGY, preferRecent, MAX_CANDIDATE_POOL, tieSeed);
            try {
                HardFilterDiagnosticCounts counts = repository.countRecommendationHardFilterStages(
                        request.priceMin(), request.priceMax(), priceUnrestricted, request.includeAdult(),
                        request.playerMin(), request.playerMax(), playersUnrestricted,
                        request.playMode() == null ? null : request.playMode().name(),
                        request.priceMode() == null ? null : request.priceMode().name());
                log.info("game_finder_recommendation_hard_filter eligible={} afterPlayMode={} "
                                + "afterPrice={} afterAdult={} afterPlayer={}", counts.getEligible(),
                        counts.getAfterPlayMode(), counts.getAfterPrice(), counts.getAfterAdult(),
                        counts.getAfterPlayer());
            } catch (RuntimeException error) {
                log.warn("game_finder_recommendation_hard_filter_diagnostic_failed errorType={}",
                        error.getClass().getSimpleName());
            }
        }
        List<Long> rankedIds;
        if (request.playMode() == null && request.priceMode() == null) {
            rankedIds = relations.findRankedRecommendationAppIds(queryTags,
                    request.priceMin(), request.priceMax(), priceUnrestricted,
                    request.includeAdult(), request.playerMin(), request.playerMax(),
                    playersUnrestricted, preferRecent, tieSeed,
                    PageRequest.of(0, MAX_CANDIDATE_POOL));
        } else {
            rankedIds = relations.findRankedRecommendationAppIds(queryTags,
                    request.priceMin(), request.priceMax(), priceUnrestricted,
                    request.includeAdult(), request.playerMin(), request.playerMax(),
                    playersUnrestricted, request.playMode() == null ? null : request.playMode().name(),
                    request.priceMode() == null ? null : request.priceMode().name(),
                    preferRecent, tieSeed, PageRequest.of(0, MAX_CANDIDATE_POOL));
        }
        Map<Long, GameFinderRecommendationCandidate> byId = repository
                .findRecommendationCandidatesByAppIds(rankedIds).stream()
                .collect(Collectors.toMap(GameFinderRecommendationCandidate::steamAppId,
                        value -> value, (left, right) -> left));
        return rankedIds.stream().distinct().map(byId::get).filter(Objects::nonNull).toList();
    }

    private long candidateTieSeed(GameFinderRecommendRequest request, Set<String> tags) {
        List<String> stableTags = tags.stream().sorted().toList();
        int hash = Objects.hash(stableTags, request.priceMin(), request.priceMax(),
                request.includeAdult(), request.playerMin(), request.playerMax(),
                request.playMode(), request.priceMode(),
                ReleasePreference.defaultIfNull(request.releasePreference()));
        return Math.floorMod((long) hash * 2_654_435_761L, 2_147_483_647L);
    }

    private Map<Long, Set<String>> tagsByAppIds(Collection<Long> appIds) {
        Map<Long, Set<String>> values = new HashMap<>();
        if (appIds == null || appIds.isEmpty()) return values;
        relations.findCanonicalNamesBySteamAppIds(appIds).forEach(value ->
                values.computeIfAbsent(value.getSteamAppId(), ignored -> new LinkedHashSet<>())
                        .add(value.getCanonicalName()));
        return values;
    }

    private double similarity(Set<String> left, Set<String> right) {
        if (left.isEmpty() || right.isEmpty()) return 0;
        long intersection = left.stream().filter(right::contains).count();
        return intersection / Math.sqrt((double) left.size() * right.size());
    }

    private double score(Set<String> seedTaste, Set<String> preferred, Set<String> candidate,
            LocalDate releaseDate, ReleasePreference releasePreference) {
        double seedScore = similarity(seedTaste, candidate);
        double tagScore = preferred.isEmpty() ? 0
                : preferred.stream().filter(candidate::contains).count() / (double) preferred.size();
        double relevance;
        if (seedTaste.isEmpty()) relevance = 0.10 + 0.90 * tagScore;
        else if (preferred.isEmpty()) relevance = seedScore;
        else relevance = 0.80 * seedScore + 0.20 * tagScore;
        // Keep the unbounded internal value for ordering. The response still caps the
        // displayed match percentage at 100, so release recency remains a soft tie/near-tie boost.
        return relevance + releaseBoost(releaseDate, releasePreference);
    }

    private double releaseBoost(LocalDate releaseDate, ReleasePreference preference) {
        ReleasePreference mode = ReleasePreference.defaultIfNull(preference);
        if (mode == ReleasePreference.ANY || releaseDate == null || releaseDate.isAfter(LocalDate.now())) return 0;
        long ageDays = Math.max(0, ChronoUnit.DAYS.between(releaseDate, LocalDate.now()));
        double freshness = ageDays <= 730 ? 1.0 : ageDays <= 1825 ? 0.6 : ageDays <= 3650 ? 0.25 : 0;
        return freshness * (mode == ReleasePreference.RECENT ? 0.08 : 0.03);
    }

    private void logRequestDiagnostics(GameFinderRecommendRequest request, Set<String> preferred,
            Map<Long, Set<String>> seedTags, Set<String> retrievalTags) {
        log.info("game_finder_recommendation_request releasePreference={} likedSteamAppIds={} "
                        + "preferredTags={} priceMin={} priceMax={} playerMin={} playerMax={} "
                        + "includeAdult={} excludeAppIds={}",
                ReleasePreference.defaultIfNull(request.releasePreference()),
                summarizeIds(request.likedSteamAppIds()), preferred, request.priceMin(),
                request.priceMax(), request.playerMin(), request.playerMax(), request.includeAdult(),
                summarizeIds(request.excludeAppIds()));
        seedTags.forEach((appId, tags) -> log.info(
                "game_finder_recommendation_seed appId={} canonicalTags={}", appId, tags));
        log.info("game_finder_recommendation_retrieval_tags tags={}", retrievalTags);
    }

    private void logCandidateDiagnostics(List<GameFinderRecommendationCandidate> candidates,
            Map<Long, Set<String>> candidateTags, Set<String> retrievalTags) {
        log.info("game_finder_recommendation_candidate_distribution total={} years={}",
                candidates.size(), candidateYearDistribution(candidates));
        for (int i = 0; i < Math.min(30, candidates.size()); i++) {
            var candidate = candidates.get(i);
            Set<String> tags = candidateTags.getOrDefault(candidate.steamAppId(), Set.of());
            log.info("game_finder_recommendation_candidate rank={} appId={} name={} "
                            + "tagMatchCount={} releaseDate={}",
                    i + 1, candidate.steamAppId(), safeName(candidate.name()),
                    retrievalTags.stream().filter(tags::contains).count(), candidate.releaseDate());
        }
    }

    private void logFinalDiagnostics(List<Scored> scored, List<Scored> diversified,
            Set<String> taste, Set<String> preferred, ReleasePreference preference) {
        Map<Long, String> buckets = diversityBuckets(scored);
        for (int i = 0; i < Math.min(30, diversified.size()); i++) {
            Scored value = diversified.get(i);
            double seedSimilarity = similarity(taste, value.tags());
            double preferredScore = preferred.isEmpty() ? 0
                    : preferred.stream().filter(value.tags()::contains).count()
                            / (double) preferred.size();
            double boost = releaseBoost(value.game().releaseDate(), preference);
            log.info("game_finder_recommendation_final rank={} appId={} name={} releaseDate={} "
                            + "tagMatchCount={} seedSimilarity={} preferredTagScore={} "
                            + "releaseBoost={} finalScore={} diversityBucket={}",
                    i + 1, value.game().steamAppId(), safeName(value.game().name()),
                    value.game().releaseDate(),
                    union(taste, preferred).stream().filter(value.tags()::contains).count(),
                    round(seedSimilarity), round(preferredScore), round(boost), round(value.score()),
                    buckets.getOrDefault(value.game().steamAppId(), "UNKNOWN"));
        }
    }

    Map<String, Long> candidateYearDistribution(List<GameFinderRecommendationCandidate> candidates) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : List.of("2026", "2025", "2024", "2020-2023",
                "2010-2019", "2009_OR_EARLIER", "FUTURE", "NULL")) result.put(key, 0L);
        for (var candidate : candidates) {
            LocalDate date = candidate.releaseDate();
            String key;
            if (date == null) key = "NULL";
            else if (date.isAfter(LocalDate.now())) key = "FUTURE";
            else if (date.getYear() == 2026) key = "2026";
            else if (date.getYear() == 2025) key = "2025";
            else if (date.getYear() == 2024) key = "2024";
            else if (date.getYear() >= 2020) key = "2020-2023";
            else if (date.getYear() >= 2010) key = "2010-2019";
            else key = "2009_OR_EARLIER";
            result.compute(key, (ignored, count) -> count + 1);
        }
        return result;
    }

    private Map<Long, String> diversityBuckets(List<Scored> scored) {
        Map<Long, String> result = new HashMap<>();
        int highEnd = Math.max(1, (int) (scored.size() * 0.35));
        int mediumEnd = Math.max(highEnd, (int) (scored.size() * 0.75));
        for (int i = 0; i < scored.size(); i++) {
            result.put(scored.get(i).game().steamAppId(),
                    i < highEnd ? "HIGH" : i < mediumEnd ? "MEDIUM" : "DISCOVERY");
        }
        return result;
    }

    private Set<String> union(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }

    private String summarizeIds(List<Long> ids) {
        if (ids == null) return "[]";
        return ids.size() <= 50 ? ids.toString() : ids.subList(0, 50) + "...(count=" + ids.size() + ")";
    }

    private double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private String safeName(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    Set<String> normalizePreferredTags(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String tag = taxonomy.normalize(value).orElseThrow(
                    () -> new IllegalArgumentException("지원하지 않는 선호 태그입니다: " + value));
            normalized.add(tag);
        }
        return normalized;
    }

    private List<Scored> diversify(List<Scored> source) {
        if (source.size() <= 1) return source;
        int highEnd = Math.max(1, (int) (source.size() * 0.35));
        int mediumEnd = Math.max(highEnd, (int) (source.size() * 0.75));
        List<Scored> high = new ArrayList<>(source.subList(0, highEnd));
        List<Scored> medium = new ArrayList<>(source.subList(highEnd, mediumEnd));
        List<Scored> discovery = new ArrayList<>(source.subList(mediumEnd, source.size()));
        List<Scored> result = new ArrayList<>(source.size());
        int highIndex = 0, mediumIndex = 0, discoveryIndex = 0;
        while (result.size() < source.size()) {
            for (int i = 0; i < 7 && highIndex < high.size(); i++) result.add(high.get(highIndex++));
            for (int i = 0; i < 2 && mediumIndex < medium.size(); i++) result.add(medium.get(mediumIndex++));
            if (discoveryIndex < discovery.size()) result.add(discovery.get(discoveryIndex++));
            if (highIndex >= high.size() && mediumIndex >= medium.size()
                    && discoveryIndex >= discovery.size()) break;
        }
        return result;
    }

    private GameFinderRecommendationResponse response(Scored value) {
        var game = value.game();
        return new GameFinderRecommendationResponse(game.steamAppId(), game.name(),
                game.headerImageUrl(), (int) Math.round(Math.min(1.0, value.score()) * 100),
                game.priceCurrent(), game.priceOriginal(), game.discountPercent(),
                game.priceCurrency(), game.isFree(), game.releaseDate(), game.releaseDateText(),
                game.comingSoon(), game.singlePlayer(), game.multiplayer(), game.onlineCoop(),
                game.maxPlayers(), new ArrayList<>(value.tags()),
                "https://store.steampowered.com/app/" + game.steamAppId());
    }

    private record Scored(GameFinderRecommendationCandidate game, double score, Set<String> tags) {}
}
