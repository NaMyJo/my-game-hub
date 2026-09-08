package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SteamGameRepositoryFinderQueryTest {
    @Autowired SteamGameRepository games;
    @Autowired SteamGameTagRepository relations;
    @Autowired JdbcTemplate jdbc;

    @Test
    void recommendationProjectionFiltersBeforeApplyingBoundedLimit() {
        insertGame(10, 5000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(20, 15000, "NON_ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(30, 5000, "ADULT", 1, 4, true, "game", "ACTIVE");
        insertGame(40, 5000, "NON_ADULT", 1, 4, false, "game", "ACTIVE");

        var result = games.findDiscoveryCandidatesFrom(0, 0, 10000, false, false,
                2, 5, false, false, PageRequest.of(0, 2));

        assertThat(result).extracting(GameFinderRecommendationCandidate::steamAppId)
                .containsExactly(10L);
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
                0, 10000, false, false, 1, 15, true, true, PageRequest.of(1, 2));

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

        var result = relations.findRelevantRecommendationAppIds(List.of("action", "rpg"),
                0, 10000, false, false, 1, 15, true, true, PageRequest.of(0, 1));

        assertThat(result).containsExactly(900000L);
    }

    private void insertGame(long appId, int price, String adult, int minPlayers,
            int maxPlayers, boolean eligible, String type, String lifecycle) {
        jdbc.update("insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                        "store_type,metadata_status,metadata_updated_at,lifecycle_status," +
                        "coming_soon,price_current,is_free,adult_status,min_players,max_players) " +
                        "values (?,?,?,?,?,?,?,?,?,?,?,?,?)",
                appId, "Game " + appId, eligible, type, "SUCCESS", Timestamp.from(Instant.now()),
                lifecycle, false, price, false, adult, minPlayers, maxPlayers);
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
