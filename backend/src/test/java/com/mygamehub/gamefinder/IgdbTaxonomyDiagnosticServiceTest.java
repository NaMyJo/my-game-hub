package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class IgdbTaxonomyDiagnosticServiceTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern QUERY_NAME = Pattern.compile("query\\s+\\S+\\s+\"([^\"]+)\"");

    @Test
    void resolvesExactAmbiguousMissingSourcesKnownMismatchAndCompositeCoverage() throws Exception {
        IgdbEnrichmentClient igdb = mock(IgdbEnrichmentClient.class);
        Map<String, JsonNode> results = new HashMap<>();
        results.put("all_genres", array(
                term(5, "Shooter", "shooter"),
                term(999, "MOBA", "moba"),
                term(77, "JRPG", "japanese-role-playing-game"),
                term(16, "Turn-based strategy (TBS)", "turn-based-strategy-tbs")));
        results.put("all_themes", array(
                term(19, "Horror", "horror"),
                term(38, "Open world", "open-world"),
                term(21, "Survival", "survival"),
                term(23, "Stealth", "stealth"),
                term(17, "Fantasy", "fantasy"),
                term(18, "Science fiction", "science-fiction"),
                term(43, "Mystery", "mystery"),
                term(39, "Warfare", "warfare")));
        results.put("all_perspectives", array(
                term(1, "First person", "first-person"),
                term(2, "Third person", "third-person")));
        results.put("all_modes", array());
        results.put("kw_soulslike", array(term(900, "Souls-like", "souls-like")));
        results.put("kw_deckbuilder", array(
                term(901, "Deckbuilder", "deckbuilder"),
                term(902, "Deck-building", "deck-building")));
        results.put("sample_horror", array(game(10, "Horror Sample")));
        results.put("sample_soulslike", array(game(11, "Souls Sample")));
        results.put("sample_fps", array(game(12, "FPS Sample")));
        results.put("sample_tps", array(game(13, "TPS Sample")));
        when(igdb.queryReadOnly(eq("multiquery"), anyString()))
                .thenAnswer(invocation -> response(invocation.getArgument(1), results));

        var report = new IgdbTaxonomyDiagnosticService(igdb).run();

        var horror = find(report, "horror");
        assertThat(horror.resolution()).isEqualTo(IgdbTaxonomyDiagnosticService.Resolution.EXACT);
        assertThat(horror.matches().get(0).source())
                .isEqualTo(IgdbTaxonomyDiagnosticService.SourceType.THEME);
        assertThat(horror.coverageFound()).isTrue();
        var soulslike = find(report, "soulslike");
        assertThat(soulslike.matches().get(0).source())
                .isEqualTo(IgdbTaxonomyDiagnosticService.SourceType.KEYWORD);
        assertThat(find(report, "jrpg").resolution())
                .isEqualTo(IgdbTaxonomyDiagnosticService.Resolution.EXACT);
        assertThat(find(report, "jrpg").matches().get(0).source())
                .isEqualTo(IgdbTaxonomyDiagnosticService.SourceType.GENRE);
        assertThat(find(report, "deckbuilder").resolution())
                .isEqualTo(IgdbTaxonomyDiagnosticService.Resolution.AMBIGUOUS);
        assertThat(find(report, "relaxing").resolution())
                .isEqualTo(IgdbTaxonomyDiagnosticService.Resolution.MISSING);
        assertThat(find(report, "moba").resolution())
                .isEqualTo(IgdbTaxonomyDiagnosticService.Resolution.KNOWN_ID_MISMATCH);
        assertThat(report.fps().coverageFound()).isTrue();
        assertThat(report.fps().componentsValid()).isTrue();
        assertThat(report.thirdPersonShooter().coverageFound()).isTrue();
        assertThat(report.thirdPersonShooter().componentsValid()).isTrue();
    }

    @Test
    void higherPriorityStructuredSourceWinsOverKeywordWithoutFalseAmbiguity() throws Exception {
        IgdbEnrichmentClient igdb = mock(IgdbEnrichmentClient.class);
        Map<String, JsonNode> results = new HashMap<>();
        results.put("all_genres", array(term(36, "MOBA", "moba")));
        results.put("all_themes", array());
        results.put("all_perspectives", array());
        results.put("all_modes", array());
        results.put("kw_moba", array(term(148, "MOBA", "moba")));
        when(igdb.queryReadOnly(eq("multiquery"), anyString()))
                .thenAnswer(invocation -> response(invocation.getArgument(1), results));

        var moba = find(new IgdbTaxonomyDiagnosticService(igdb).run(), "moba");

        assertThat(moba.resolution()).isEqualTo(IgdbTaxonomyDiagnosticService.Resolution.EXACT);
        assertThat(moba.matches()).extracting(match -> match.source())
                .containsExactly(IgdbTaxonomyDiagnosticService.SourceType.GENRE,
                        IgdbTaxonomyDiagnosticService.SourceType.KEYWORD);
    }

    @Test
    void everyRequestIsBoundedAndMultiqueryNeverExceedsOfficialSubqueryLimit() throws Exception {
        IgdbEnrichmentClient igdb = mock(IgdbEnrichmentClient.class);
        List<String> bodies = new ArrayList<>();
        when(igdb.queryReadOnly(eq("multiquery"), anyString())).thenAnswer(invocation -> {
            String body = invocation.getArgument(1);
            bodies.add(body);
            return response(body, Map.of());
        });

        new IgdbTaxonomyDiagnosticService(igdb).run();

        assertThat(bodies).isNotEmpty();
        assertThat(bodies).allSatisfy(body -> {
            assertThat(queryNames(body).size()).isLessThanOrEqualTo(10);
            assertThat(body).contains("limit ");
            assertThat(body).doesNotContain("offset");
        });
        verify(igdb, never()).findBySteamAppIds(org.mockito.ArgumentMatchers.anyCollection());
    }

    private static IgdbTaxonomyDiagnosticService.TermResult find(
            IgdbTaxonomyDiagnosticService.DiagnosticReport report, String canonical) {
        return report.terms().stream().filter(term -> term.canonical().equals(canonical))
                .findFirst().orElseThrow();
    }

    private static JsonNode response(String body, Map<String, JsonNode> results) {
        var response = JSON.createArrayNode();
        for (String name : queryNames(body)) {
            response.addObject().put("name", name).set("result",
                    results.getOrDefault(name, JSON.createArrayNode()));
        }
        return response;
    }

    private static List<String> queryNames(String body) {
        List<String> names = new ArrayList<>();
        Matcher matcher = QUERY_NAME.matcher(body);
        while (matcher.find()) names.add(matcher.group(1));
        return names;
    }

    private static JsonNode array(JsonNode... nodes) {
        var array = JSON.createArrayNode();
        for (JsonNode node : nodes) array.add(node);
        return array;
    }

    private static JsonNode term(long id, String name, String slug) {
        return JSON.createObjectNode().put("id", id).put("name", name).put("slug", slug);
    }

    private static JsonNode game(long id, String name) {
        return JSON.createObjectNode().put("id", id).put("name", name);
    }
}
