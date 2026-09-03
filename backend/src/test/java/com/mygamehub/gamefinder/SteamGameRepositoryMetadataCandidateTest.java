package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class SteamGameRepositoryMetadataCandidateTest {
    @Autowired SteamGameRepository games;
    @Autowired JdbcTemplate jdbc;

    @Test
    void pendingThenStaleSuccessThenCooledRetryableAndFreshRetryIsExcluded() {
        Instant now = Instant.now();
        insert(30, "pending", "PENDING", null, null);
        insert(20, "stale", "SUCCESS", now.minusSeconds(8 * 86_400), now.minusSeconds(86_400));
        insert(10, "cooled-retry", "RETRYABLE_FAILURE", null, now.minusSeconds(21 * 60));
        insert(5, "fresh-retry", "RETRYABLE_FAILURE", null, now.minusSeconds(60));

        var candidates = games.findMetadataCandidates(now.minusSeconds(7 * 86_400),
                now.minusSeconds(20 * 60), PageRequest.of(0, 10));

        assertEquals(java.util.List.of(30L, 20L, 10L), candidates.stream()
                .map(SteamGame::getSteamAppId).toList());
        assertEquals(3, games.countMetadataCandidates(now.minusSeconds(7 * 86_400),
                now.minusSeconds(20 * 60)));
    }

    @Test
    void initialPopulationExcludesStaleSuccessAndReportsCoolingRetryTimestamp() {
        Instant now = Instant.now();
        insert(1, "pending", "PENDING", null, null);
        insert(2, "terminal", "SUCCESS", now.minusSeconds(30 * 86_400), now.minusSeconds(60));
        insert(3, "ready-retry", "RETRYABLE_FAILURE", null, now.minusSeconds(21 * 60));
        insert(4, "cooling-retry", "RETRYABLE_FAILURE", null, now.minusSeconds(5 * 60));

        var candidates = games.findInitialMetadataCandidates(now.minusSeconds(20 * 60),
                PageRequest.of(0, 10));

        assertEquals(java.util.List.of(1L, 3L), candidates.stream()
                .map(SteamGame::getSteamAppId).toList());
        assertEquals(3, games.countInitialMetadataIncomplete());
        assertEquals(1, games.countCoolingMetadataRetryable(now.minusSeconds(20 * 60)));
        long secondsAgo = now.getEpochSecond()
                - games.findOldestCoolingMetadataAttempt(now.minusSeconds(20 * 60))
                    .orElseThrow().getEpochSecond();
        assertTrue(secondsAgo >= 299 && secondsAgo <= 301);
    }

    private void insert(long appId, String name, String status, Instant updatedAt,
            Instant attemptedAt) {
        jdbc.update("insert into steam_games (steam_app_id,name,game_catalog_eligible," +
                        "metadata_status,metadata_updated_at,metadata_last_attempt_at,coming_soon) " +
                        "values (?,?,?,?,?,?,false)", appId, name, true, status,
                updatedAt == null ? null : Timestamp.from(updatedAt),
                attemptedAt == null ? null : Timestamp.from(attemptedAt));
    }
}
