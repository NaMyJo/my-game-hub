package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import static org.assertj.core.api.Assertions.assertThat;

class GameTagTaxonomyTest {
    private final GameTagTaxonomy taxonomy = new GameTagTaxonomy();

    @Test
    void exposesExactlyFortySevenActiveCanonicalTags() {
        assertThat(taxonomy.currentCanonicalNames()).hasSize(47).contains(
                "action", "online-coop", "horror", "moba", "soulslike", "fps",
                "third-person-shooter", "fantasy", "choices-matter");
        assertThat(taxonomy.currentCanonicalNames()).doesNotContain(
                "deckbuilder", "card-game", "zombies", "difficult");
    }

    @Test
    void normalizesActiveEnglishAndKoreanAliasesAndKeepsLegacyCompatibility() {
        taxonomy.currentCanonicalNames().forEach(canonical ->
                assertThat(taxonomy.normalize(canonical)).contains(canonical));
        assertThat(taxonomy.normalize("\uacf5\ud3ec")).contains("horror");
        assertThat(taxonomy.normalize("AOS")).contains("moba");
        assertThat(taxonomy.normalize("\uc624\ud508 \uc6d4\ub4dc")).contains("open-world");
        assertThat(taxonomy.normalize("science fiction")).contains("sci-fi");
        assertThat(taxonomy.normalize("deck-building")).contains("deckbuilder");
    }

    @Test
    void steamGenerationRemainsLimitedToStructuredSteamV1Evidence() {
        SteamGame game = game(Set.of("Action", "Adventure", "RPG", "Strategy",
                        "Simulation", "Sports", "Racing", "Casual", "Indie", "Massively Multiplayer"),
                Set.of("PvP", "Online PvP"), true, true, true, true, true, true,
                "horror moba open world soulslike must be ignored");
        assertThat(taxonomy.fromSteam(game)).hasSize(19).doesNotContain(
                "horror", "moba", "open-world", "soulslike");
    }

    @Test
    void mapsIgdbTermsOnlyByWhitelistedSourceAndId() {
        assertThat(taxonomy.fromIgdb(List.of(
                term(IgdbTaxonomySourceType.THEME, 19), term(IgdbTaxonomySourceType.GENRE, 36),
                term(IgdbTaxonomySourceType.THEME, 38), term(IgdbTaxonomySourceType.KEYWORD, 17326),
                term(IgdbTaxonomySourceType.KEYWORD, 416), term(IgdbTaxonomySourceType.KEYWORD, 17292),
                term(IgdbTaxonomySourceType.KEYWORD, 477))))
                .containsExactlyInAnyOrder("horror", "moba", "open-world", "soulslike",
                        "roguelike", "roguelite", "metroidvania");
        assertThat(taxonomy.fromIgdb(List.of(
                new IgdbTaxonomyValue(IgdbTaxonomySourceType.KEYWORD, 999, "horror", "horror"))))
                .isEmpty();
    }

    @Test
    void fpsAndThirdPersonShooterRequireShooterAndPerspective() {
        var shooter = term(IgdbTaxonomySourceType.GENRE, 5);
        var first = term(IgdbTaxonomySourceType.PERSPECTIVE, 1);
        var third = term(IgdbTaxonomySourceType.PERSPECTIVE, 2);
        assertThat(taxonomy.fromIgdb(List.of(shooter))).doesNotContain("fps", "third-person-shooter");
        assertThat(taxonomy.fromIgdb(List.of(first))).doesNotContain("fps");
        assertThat(taxonomy.fromIgdb(List.of(shooter, first))).contains("fps");
        assertThat(taxonomy.fromIgdb(List.of(shooter, third))).contains("third-person-shooter");
    }

    @Test
    void descriptionKeywordsNeverCreateTags() {
        SteamGame game = game(Set.of(), Set.of(), false, false, false, false,
                false, false, "Action RPG multiplayer horror moba open-world");
        assertThat(taxonomy.fromSteam(game)).isEmpty();
    }

    private static IgdbTaxonomyValue term(IgdbTaxonomySourceType type, long id) {
        return new IgdbTaxonomyValue(type, id, "ignored", "ignored");
    }

    private SteamGame game(Set<String> genres, Set<String> categories,
            boolean single, boolean multiplayer, boolean onlineCoop, boolean localCoop,
            boolean free, boolean earlyAccess, String description) {
        SteamGame game = new SteamGame(10, "Game", 0, 0);
        game.updateStoreDetail("game", null, description, free, "KRW", 0, 0, 0,
                0, "NON_ADULT", null, null, false, earlyAccess, genres, categories,
                single, multiplayer, onlineCoop, localCoop);
        return game;
    }
}
