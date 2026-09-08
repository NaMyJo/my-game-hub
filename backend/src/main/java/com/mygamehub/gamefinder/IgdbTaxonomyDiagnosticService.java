package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class IgdbTaxonomyDiagnosticService {
    private static final Logger log = LoggerFactory.getLogger(IgdbTaxonomyDiagnosticService.class);
    static final int MAX_SUBQUERIES = 10;
    static final int TERM_LIMIT = 10;
    static final int SAMPLE_LIMIT = 3;

    private static final List<Candidate> CANDIDATES = List.of(
            candidate("horror"), candidate("moba"), candidate("open-world", "open-world", "openworld"),
            candidate("survival"), candidate("stealth"), candidate("fantasy"),
            candidate("sci-fi", "sci-fi", "science-fiction"), candidate("mystery"),
            candidate("turn-based-strategy", "turn-based-strategy", "turn-based-strategy-tbs"),
            candidate("warfare"),
            candidate("soulslike", "soulslike", "souls-like"), candidate("jrpg"),
            candidate("turn-based"), candidate("roguelike"), candidate("roguelite"),
            candidate("crafting"), candidate("metroidvania"),
            candidate("tower-defense"), candidate("deckbuilder", "deckbuilder", "deck-building"),
            candidate("card-game"), candidate("bullet-hell"), candidate("medieval"),
            candidate("zombies", "zombies", "zombie"), candidate("space"),
            candidate("military"), candidate("post-apocalyptic"), candidate("story-rich"),
            candidate("relaxing"), candidate("difficult"), candidate("choices-matter"));

    private static final Map<String, KnownTerm> KNOWN = Map.ofEntries(
            known("horror", SourceType.THEME, 19, "Horror", "horror"),
            known("moba", SourceType.GENRE, 36, "MOBA", "moba"),
            known("open-world", SourceType.THEME, 38, "Open world", "open-world"),
            known("survival", SourceType.THEME, 21, "Survival", "survival"),
            known("stealth", SourceType.THEME, 23, "Stealth", "stealth"),
            known("fantasy", SourceType.THEME, 17, "Fantasy", "fantasy"),
            known("sci-fi", SourceType.THEME, 18, "Science fiction", "science-fiction"),
            known("mystery", SourceType.THEME, 43, "Mystery", "mystery"),
            known("turn-based-strategy", SourceType.GENRE, 16,
                    "Turn-based strategy (TBS)", "turn-based-strategy-tbs"),
            known("warfare", SourceType.THEME, 39, "Warfare", "warfare"));

    private final IgdbEnrichmentClient igdb;

    public IgdbTaxonomyDiagnosticService(IgdbEnrichmentClient igdb) {
        this.igdb = igdb;
    }

    public DiagnosticReport run() {
        Map<String, List<JsonNode>> referenceResults = executeMultiQueries(referenceQueries());
        Map<String, List<JsonNode>> keywordResults = executeMultiQueries(keywordQueries());
        List<TermResult> terms = resolveTerms(referenceResults, keywordResults);
        boolean fpsComponentsValid = compositeComponentsValid(referenceResults, 5, "shooter",
                1, "first-person");
        boolean tpsComponentsValid = compositeComponentsValid(referenceResults, 5, "shooter",
                2, "third-person");
        Map<String, List<JsonNode>> coverage = executeMultiQueries(
                coverageQueries(terms, fpsComponentsValid, tpsComponentsValid));
        terms = attachCoverage(terms, coverage);
        CompositeResult fps = composite("fps", 5, 1, fpsComponentsValid, coverage);
        CompositeResult tps = composite("third-person-shooter", 5, 2, tpsComponentsValid, coverage);
        logReport(terms, fps, tps);
        return new DiagnosticReport(List.copyOf(terms), fps, tps);
    }

    private List<Query> referenceQueries() {
        return List.of(
                new Query("all_genres", "genres", "fields id,name,slug; limit 500;"),
                new Query("all_themes", "themes", "fields id,name,slug; limit 500;"),
                new Query("all_perspectives", "player_perspectives", "fields id,name,slug; limit 500;"),
                new Query("all_modes", "game_modes", "fields id,name,slug; limit 500;"));
    }

    private List<Query> keywordQueries() {
        List<Query> queries = new ArrayList<>();
        for (Candidate candidate : CANDIDATES) {
            String slugs = candidate.aliases().stream().map(IgdbTaxonomyDiagnosticService::quoted)
                    .reduce((left, right) -> left + "," + right).orElse(quoted(candidate.canonical()));
            queries.add(new Query("kw_" + safeName(candidate.canonical()), "keywords",
                    "fields id,name,slug; where slug = (" + slugs + "); limit " + TERM_LIMIT + ";"));
        }
        return queries;
    }

    private List<TermResult> resolveTerms(Map<String, List<JsonNode>> references,
            Map<String, List<JsonNode>> keywords) {
        List<TermResult> results = new ArrayList<>();
        for (Candidate candidate : CANDIDATES) {
            List<Term> matches = new ArrayList<>();
            addMatches(matches, candidate, SourceType.GENRE, references.get("all_genres"));
            addMatches(matches, candidate, SourceType.THEME, references.get("all_themes"));
            addMatches(matches, candidate, SourceType.PLAYER_PERSPECTIVE, references.get("all_perspectives"));
            addMatches(matches, candidate, SourceType.GAME_MODE, references.get("all_modes"));
            addMatches(matches, candidate, SourceType.KEYWORD,
                    keywords.get("kw_" + safeName(candidate.canonical())));
            matches = matches.stream().distinct().sorted(Comparator.comparing(Term::priority)
                    .thenComparingLong(Term::id)).toList();
            int preferredPriority = matches.isEmpty() ? Integer.MAX_VALUE : matches.get(0).priority();
            long preferredMatchCount = matches.stream()
                    .filter(term -> term.priority() == preferredPriority).count();
            Resolution resolution = matches.isEmpty() ? Resolution.MISSING
                    : preferredMatchCount == 1 ? Resolution.EXACT : Resolution.AMBIGUOUS;
            KnownTerm expected = KNOWN.get(candidate.canonical());
            boolean knownMismatch = expected != null && matches.stream().noneMatch(expected::matches);
            if (knownMismatch) resolution = Resolution.KNOWN_ID_MISMATCH;
            results.add(new TermResult(candidate.canonical(), resolution, matches, expected,
                    false, List.of(), recommendation(resolution, matches), null));
        }
        return results;
    }

    private static void addMatches(List<Term> target, Candidate candidate, SourceType source,
            List<JsonNode> nodes) {
        if (nodes == null) return;
        Set<String> aliases = new LinkedHashSet<>(candidate.aliases());
        for (JsonNode node : nodes) {
            String slug = normalize(node.path("slug").asText(""));
            String name = normalize(node.path("name").asText(""));
            if (aliases.contains(slug) || aliases.contains(name)) {
                target.add(new Term(source, node.path("id").asLong(),
                        node.path("name").asText(""), node.path("slug").asText("")));
            }
        }
    }

    private List<Query> coverageQueries(List<TermResult> terms, boolean fpsComponentsValid,
            boolean tpsComponentsValid) {
        List<Query> queries = new ArrayList<>();
        for (TermResult result : terms) {
            if (result.resolution() != Resolution.EXACT) continue;
            Term term = result.matches().get(0);
            queries.add(new Query("sample_" + safeName(result.canonical()), "games",
                    "fields id,name; where " + term.source().gameField() + " = (" + term.id()
                            + "); limit " + SAMPLE_LIMIT + ";"));
        }
        if (fpsComponentsValid) queries.add(compositeQuery("sample_fps", 5, 1));
        if (tpsComponentsValid) queries.add(compositeQuery("sample_tps", 5, 2));
        return queries;
    }

    private static Query compositeQuery(String name, long genreId, long perspectiveId) {
        return new Query(name, "games", "fields id,name; where genres = (" + genreId
                + ") & player_perspectives = (" + perspectiveId + "); limit " + SAMPLE_LIMIT + ";");
    }

    private List<TermResult> attachCoverage(List<TermResult> terms,
            Map<String, List<JsonNode>> coverage) {
        return terms.stream().map(result -> {
            List<GameSample> samples = samples(coverage.get("sample_" + safeName(result.canonical())));
            String recommendation = result.resolution() != Resolution.EXACT ? "DO_NOT_ADD"
                    : samples.isEmpty() ? "REVIEW_NO_COVERAGE"
                    : result.matches().get(0).source() == SourceType.KEYWORD
                            ? "REVIEW_KEYWORD_COVERAGE" : "V2_CANDIDATE";
            return new TermResult(result.canonical(), result.resolution(), result.matches(),
                    result.expected(), !samples.isEmpty(), samples, recommendation,
                    samples.isEmpty() ? "NO_BOUNDED_SAMPLE" : "BOUNDED_SAMPLE_FOUND");
        }).toList();
    }

    private static CompositeResult composite(String canonical, long genreId, long perspectiveId,
            boolean componentsValid, Map<String, List<JsonNode>> coverage) {
        List<GameSample> samples = samples(coverage.get("sample_" + (canonical.equals("fps") ? "fps" : "tps")));
        return new CompositeResult(canonical, genreId, perspectiveId, componentsValid,
                !samples.isEmpty(), samples);
    }

    private static boolean compositeComponentsValid(Map<String, List<JsonNode>> references,
            long genreId, String genreSlug, long perspectiveId, String perspectiveSlug) {
        return contains(references.get("all_genres"), genreId, genreSlug)
                && contains(references.get("all_perspectives"), perspectiveId, perspectiveSlug);
    }

    private static boolean contains(List<JsonNode> nodes, long id, String slug) {
        return nodes != null && nodes.stream().anyMatch(node -> node.path("id").asLong() == id
                && normalize(node.path("slug").asText("")).equals(normalize(slug)));
    }

    private Map<String, List<JsonNode>> executeMultiQueries(List<Query> queries) {
        Map<String, List<JsonNode>> results = new LinkedHashMap<>();
        for (int from = 0; from < queries.size(); from += MAX_SUBQUERIES) {
            List<Query> chunk = queries.subList(from, Math.min(from + MAX_SUBQUERIES, queries.size()));
            StringBuilder body = new StringBuilder();
            for (Query query : chunk) {
                body.append("query ").append(query.endpoint()).append(" \"")
                        .append(query.name()).append("\" { ").append(query.body()).append(" };\n");
            }
            JsonNode response = igdb.queryReadOnly("multiquery", body.toString());
            if (response != null && response.isArray()) {
                for (JsonNode item : response) {
                    List<JsonNode> rows = new ArrayList<>();
                    JsonNode result = item.path("result");
                    if (result.isArray()) result.forEach(rows::add);
                    results.put(item.path("name").asText(), List.copyOf(rows));
                }
            }
            chunk.forEach(query -> results.putIfAbsent(query.name(), List.of()));
        }
        return results;
    }

    private static List<GameSample> samples(List<JsonNode> nodes) {
        if (nodes == null) return List.of();
        return nodes.stream().limit(SAMPLE_LIMIT)
                .map(node -> new GameSample(node.path("id").asLong(), node.path("name").asText("")))
                .toList();
    }

    private static String recommendation(Resolution resolution, List<Term> matches) {
        if (resolution == Resolution.EXACT && !matches.isEmpty()) {
            return matches.get(0).source() == SourceType.KEYWORD ? "REVIEW_KEYWORD_COVERAGE" : "V2_CANDIDATE";
        }
        return "DO_NOT_ADD";
    }

    private void logReport(List<TermResult> terms, CompositeResult fps, CompositeResult tps) {
        log.info("igdb_taxonomy_diagnostic_start candidates={} sampleLimit={}", terms.size() + 2, SAMPLE_LIMIT);
        for (TermResult term : terms) {
            log.info("igdb_taxonomy_term canonical={} resolution={} expected={} matches={} coverage={} recommendation={} samples={}",
                    term.canonical(), term.resolution(), term.expected(), term.matches(), term.coverageFound(),
                    term.v2Recommendation(), term.samples());
        }
        log.info("igdb_taxonomy_composite canonical=fps genreId={} perspectiveId={} componentsValid={} coverage={} samples={}",
                fps.genreId(), fps.perspectiveId(), fps.componentsValid(), fps.coverageFound(), fps.samples());
        log.info("igdb_taxonomy_composite canonical=third-person-shooter genreId={} perspectiveId={} componentsValid={} coverage={} samples={}",
                tps.genreId(), tps.perspectiveId(), tps.componentsValid(), tps.coverageFound(), tps.samples());
        log.info("igdb_taxonomy_diagnostic_complete");
    }

    private static Candidate candidate(String canonical, String... aliases) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        values.add(normalize(canonical));
        for (String alias : aliases) values.add(normalize(alias));
        return new Candidate(canonical, List.copyOf(values));
    }

    private static Map.Entry<String, KnownTerm> known(String canonical, SourceType source,
            long id, String name, String slug) {
        return Map.entry(canonical, new KnownTerm(source, id, name, slug));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT)
                .replace('_', '-').replace(' ', '-');
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\"", "") + "\"";
    }

    private static String safeName(String value) { return value.replace('-', '_'); }

    enum SourceType {
        GENRE("genres", 0), THEME("themes", 1), PLAYER_PERSPECTIVE("player_perspectives", 2),
        GAME_MODE("game_modes", 3), KEYWORD("keywords", 4);
        private final String gameField;
        private final int priority;
        SourceType(String gameField, int priority) { this.gameField = gameField; this.priority = priority; }
        String gameField() { return gameField; }
        int priority() { return priority; }
    }

    enum Resolution { EXACT, AMBIGUOUS, MISSING, KNOWN_ID_MISMATCH }
    record Candidate(String canonical, List<String> aliases) {}
    record Query(String name, String endpoint, String body) {}
    record Term(SourceType source, long id, String name, String slug) {
        int priority() { return source.priority(); }
    }
    record KnownTerm(SourceType source, long id, String name, String slug) {
        boolean matches(Term term) {
            return source == term.source() && id == term.id()
                    && normalize(name).equals(normalize(term.name()))
                    && normalize(slug).equals(normalize(term.slug()));
        }
    }
    public record GameSample(long id, String name) {}
    public record TermResult(String canonical, Resolution resolution, List<Term> matches,
            KnownTerm expected, boolean coverageFound, List<GameSample> samples,
            String v2Recommendation, String coverageAssessment) {}
    public record CompositeResult(String canonical, long genreId, long perspectiveId,
            boolean componentsValid, boolean coverageFound, List<GameSample> samples) {}
    public record DiagnosticReport(List<TermResult> terms, CompositeResult fps,
            CompositeResult thirdPersonShooter) {}
}
