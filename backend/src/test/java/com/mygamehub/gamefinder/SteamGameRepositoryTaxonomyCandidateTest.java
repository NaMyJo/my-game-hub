package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SteamGameRepositoryTaxonomyCandidateTest {
    @Autowired SteamGameRepository games;
    @Autowired JdbcTemplate jdbc;

    @Test
    void selectsOnlyOutdatedEligibleGamesInSteamAppIdOrder() {
        insert(30, null, true, "game", "SUCCESS", "ACTIVE");
        insert(10, "old-version", true, "game", "SUCCESS", "ACTIVE");
        insert(20, GameTagTaxonomy.CURRENT_VERSION, true, "game", "SUCCESS", "ACTIVE");
        insert(5, null, false, "game", "SUCCESS", "ACTIVE");
        insert(6, null, true, "dlc", "SUCCESS", "ACTIVE");
        insert(7, null, true, "game", "PENDING", "ACTIVE");
        insert(8, null, true, "game", "SUCCESS", "REMOVED");

        var result = games.findTaxonomyVersionCandidates(
                GameTagTaxonomy.CURRENT_VERSION, PageRequest.of(0, 100));

        assertThat(result).extracting(SteamGame::getSteamAppId)
                .containsExactly(10L, 30L);
    }

    private void insert(long appId, String version, boolean eligible, String type,
            String metadataStatus, String lifecycle) {
        jdbc.update("insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                        "store_type,metadata_status,metadata_updated_at,lifecycle_status," +
                        "taxonomy_version,coming_soon) values (?,?,?,?,?,?,?,?,false)",
                appId, "Game " + appId, eligible, type, metadataStatus,
                Timestamp.from(Instant.now()), lifecycle, version);
    }
}
