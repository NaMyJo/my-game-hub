package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.ArrayList;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SteamGameRepositoryFinderQueryTest {
    @Autowired SteamGameRepository games;
    @Autowired SteamGameTagRepository relations;
    @Autowired JdbcTemplate jdbc;

    @Test
    void rankedRecommendationQueryFiltersBeforeApplyingBoundedLimit() {
        insertGame(10, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(20, 15000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(30, 5000, "ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(40, 5000, "NON_ADULT", 1, 4, false, "game", "ACTIVE");

        var result = relations.findRankedRecommendationAppIds(List.of("__none__"),
                0, 10000, false, false, 2, 5, false,
                false, 17, PageRequest.of(0, 2));

        assertThat(result).containsExactly(10L);
    }

    @Test
    void tagSearchAppliesHardFiltersAndDatabasePagination() {
        long coop = insertTag("coop");
        for (long appId : List.of(10L, 20L, 30L)) {
            insertGame(appId, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
            jdbc.update("insert into steam_game_tags (steam_app_id,tag_id,source) values (?,?,?)",
                    appId, coop, "STEAM");
        }

        var result = relations.findFilteredAppIdsMatchingAll(List.of("coop"), 1,
                0, 10000, false, false, 1, 15, true, PageRequest.of(1, 2));

        assertThat(result).containsExactly(30L);
    }

    @Test
    void relevantCandidatesAreRankedByTagMatchesBeforeBoundedLimitRegardlessOfAppId() {
        long action = insertTag("action");
        long rpg = insertTag("rpg");
        insertGame(10, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(900000, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertRelation(10, action);
        insertRelation(900000, action);
        insertRelation(900000, rpg);

        var result = relations.findRankedRecommendationAppIds(List.of("action", "rpg"),
                0, 10000, false, false, 1, 15, true,
                false, 17, PageRequest.of(0, 1));

        assertThat(result).containsExactly(900000L);
    }

    @Test
    void highAppIdRelevantGameSurvivesTwoThousandCandidateLimit() {
        long action = insertTag("action");
        String sql = "insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                "store_type,metadata_status,metadata_updated_at,lifecycle_status," +
                "coming_soon,price_current,is_free,adult_status,min_players,max_players) " +
                "values (?,?,?,?,?,?,?,?,?,?,?,?,?)";
        var rows = new ArrayList<Object[]>();
        for (long appId = 1; appId <= 2001; appId++) {
            rows.add(new Object[] {appId, "Low " + appId, true, "game", "SUCCESS",
                    Timestamp.from(Instant.now()), "ACTIVE", false, 0, true,
                    "NON_ADULT", 1, 4});
        }
        jdbc.batchUpdate(sql, rows);
        insertGame(900000, 0, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertRelation(900000, action);

        var result = relations.findRankedRecommendationAppIds(List.of("action"),
                0, 100000, true, false, 1, 15, true,
                false, 71, PageRequest.of(0, 2000));

        assertThat(result).hasSize(2000).contains(900000L);
    }

    @Test
    void recentRanksNewerReleaseFirstOnlyWhenTagRelevanceIsEqual() {
        long action = insertTag("action");
        long rpg = insertTag("rpg");
        insertGame(10, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE",
                java.time.LocalDate.of(2019, 1, 1));
        insertGame(900000, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE",
                java.time.LocalDate.of(2026, 1, 1));
        insertRelation(10, action);
        insertRelation(10, rpg);
        insertRelation(900000, action);
        insertRelation(900000, rpg);

        var result = relations.findRankedRecommendationAppIds(List.of("action", "rpg"),
                0, 10000, false, false, 1, 15, true,
                true, 17, PageRequest.of(0, 2));

        assertThat(result).containsExactly(900000L, 10L);
    }

    @Test
    void recentNeverLetsNewerOneTagMatchBeatOlderThreeTagMatches() {
        long action = insertTag("action");
        long rpg = insertTag("rpg");
        long fantasy = insertTag("fantasy");
        insertGame(10, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE",
                java.time.LocalDate.of(2019, 1, 1));
        insertGame(900000, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE",
                java.time.LocalDate.of(2026, 1, 1));
        insertRelation(10, action);
        insertRelation(10, rpg);
        insertRelation(10, fantasy);
        insertRelation(900000, action);

        var result = relations.findRankedRecommendationAppIds(
                List.of("action", "rpg", "fantasy"), 0, 10000, false, false,
                1, 15, true, true, 17, PageRequest.of(0, 2));

        assertThat(result).containsExactly(10L, 900000L);
    }

    @Test
    void playerFilterUsesRangeOverlapAndKnownOnlineCapacity() {
        long action = insertTag("action");
        insertGame(10, 30000, "NON_ADULT", 1, 8, true, "game", "ACTIVE");
        insertGame(20, 30000, "NON_ADULT", 2, 10, true, "game", "ACTIVE");
        insertGame(30, 30000, "NON_ADULT", 4, 6, true, "game", "ACTIVE");
        insertGame(40, 30000, "NON_ADULT", 1, 2, true, "game", "ACTIVE");
        insertGame(50, 30000, "NON_ADULT", 8, 12, true, "game", "ACTIVE");
        for (long appId : List.of(10L, 20L, 30L, 40L, 50L)) insertRelation(appId, action);
        insertLegacyOnlineCapacityGame(60, 30000, 8, 6);
        insertRelation(60, action);

        var result = relations.findRankedRecommendationAppIds(List.of("action"),
                20000, 41000, false, false, 4, 6, false,
                true, 17, PageRequest.of(0, 20));

        assertThat(result).containsExactlyInAnyOrder(10L, 20L, 30L, 60L)
                .doesNotContain(40L, 50L);
    }

    @Test
    void hardFilterDiagnosticCountsEachStageWithoutLoadingEntities() {
        insertGame(10, 30000, "NON_ADULT", 1, 8, true, "game", "ACTIVE");
        insertGame(20, 10000, "NON_ADULT", 1, 8, true, "game", "ACTIVE");
        insertGame(30, 30000, "ADULT", 1, 8, true, "game", "ACTIVE");
        insertGame(40, 30000, "NON_ADULT", 1, 2, true, "game", "ACTIVE");

        var counts = games.countRecommendationHardFilterStages(
                20000, 41000, false, false, 4, 6, false);

        assertThat(counts.getEligible()).isEqualTo(4);
        assertThat(counts.getAfterPrice()).isEqualTo(3);
        assertThat(counts.getAfterAdult()).isEqualTo(2);
        assertThat(counts.getAfterPlayer()).isEqualTo(1);
    }

    @Test
    void adminStatusCountsKnownAndMissingPlayerDataWithoutLoadingEntities() {
        insertGame(10, 30000, "NON_ADULT", 1, 8, true, "game", "ACTIVE");
        insertLegacyOnlineCapacityGame(20, 30000, 8, 6);
        insertGameWithoutPlayerData(30, 30000);

        var status = games.adminStatus();

        assertThat(status.getPlayerDataCount()).isEqualTo(2);
        assertThat(status.getPlayerDataMissingCount()).isEqualTo(1);
    }

    @Test
    void igdbIntegrityUsesAggregateChecksForStatusAndPlayerRanges() {
        insertGame(10, 30000, "NON_ADULT", 1, 8, true, "game", "ACTIVE");
        jdbc.update("update steam_games set igdb_status='SUCCESS',igdb_game_id=100 where steam_app_id=10");
        insertGameWithoutPlayerData(20, 30000);
        jdbc.update("update steam_games set igdb_status='SUCCESS' where steam_app_id=20");
        insertGame(30, 30000, "NON_ADULT", 8, 2, true, "game", "ACTIVE");
        jdbc.update("update steam_games set igdb_status='SUCCESS',igdb_game_id=300 where steam_app_id=30");

        var result = games.verifyIgdbIntegrity();

        assertThat(result.getTotalChecked()).isEqualTo(3);
        assertThat(result.getSuccessMissingGameId()).isEqualTo(1);
        assertThat(result.getInvalidPlayerRange()).isEqualTo(1);
        assertThat(result.getNotFoundWithGameId()).isZero();
    }

    private void insertGame(long appId, int price, String adult, int minPlayers,
            int maxPlayers, boolean eligible, String type, String lifecycle) {
        insertGame(appId, price, adult, minPlayers, maxPlayers, eligible, type, lifecycle, null);
    }

    private void insertGame(long appId, int price, String adult, int minPlayers,
            int maxPlayers, boolean eligible, String type, String lifecycle,
            java.time.LocalDate releaseDate) {
        jdbc.update("insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                        "store_type,metadata_status,metadata_updated_at,lifecycle_status," +
                        "coming_soon,price_current,is_free,adult_status,min_players,max_players,release_date) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                appId, "Game " + appId, eligible, type, "SUCCESS", Timestamp.from(Instant.now()),
                lifecycle, false, price, false, adult, minPlayers, maxPlayers, releaseDate);
    }

    private void insertLegacyOnlineCapacityGame(long appId, int price, int onlineMax,
            int onlineCoopMax) {
        jdbc.update("insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                        "store_type,metadata_status,metadata_updated_at,lifecycle_status," +
                        "coming_soon,price_current,is_free,adult_status,min_players,max_players," +
                        "online_max_players,online_coop_max_players) values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                appId, "Game " + appId, true, "game", "SUCCESS", Timestamp.from(Instant.now()),
                "ACTIVE", false, price, false, "NON_ADULT", null, null, onlineMax, onlineCoopMax);
    }

    private void insertGameWithoutPlayerData(long appId, int price) {
        jdbc.update("insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                        "store_type,metadata_status,metadata_updated_at,lifecycle_status," +
                        "coming_soon,price_current,is_free,adult_status) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?)",
                appId, "Game " + appId, true, "game", "SUCCESS", Timestamp.from(Instant.now()),
                "ACTIVE", false, price, false, "NON_ADULT");
    }

    private long insertTag(String canonicalName) {
        jdbc.update("insert into game_tags (canonical_name,display_name_ko,type) values (?,?,?)",
                canonicalName, canonicalName, "PLAY_STYLE");
        return jdbc.queryForObject("select id from game_tags where canonical_name=?",
                Long.class, canonicalName);
    }

    private void insertRelation(long appId, long tagId) {
        jdbc.update("insert into steam_game_tags (steam_app_id,tag_id,source) values (?,?,?)",
                appId, tagId, "STEAM");
    }
}
