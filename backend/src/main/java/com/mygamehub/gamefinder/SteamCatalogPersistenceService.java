package com.mygamehub.gamefinder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SteamCatalogPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(SteamCatalogPersistenceService.class);
    private static final String INSERT_PREFIX = """
            INSERT INTO steam_games
                (steam_app_id, name, store_type, steam_last_modified,
                 steam_price_change_number, adult_status, coming_soon,
                 lifecycle_status, last_seen_at, reconciliation_generation,
                 game_catalog_eligible)
            VALUES
            """;
    private static final String UPSERT_SUFFIX = """
            ON CONFLICT (steam_app_id) DO UPDATE SET
                name = EXCLUDED.name,
                steam_last_modified = EXCLUDED.steam_last_modified,
                metadata_updated_at = CASE
                    WHEN steam_games.steam_last_modified IS DISTINCT FROM EXCLUDED.steam_last_modified
                    THEN NULL ELSE steam_games.metadata_updated_at END,
                price_updated_at = CASE
                    WHEN steam_games.steam_price_change_number IS NOT NULL
                     AND steam_games.steam_price_change_number
                         <> EXCLUDED.steam_price_change_number
                    THEN NULL
                    ELSE steam_games.price_updated_at
                END,
                metadata_status = CASE
                    WHEN COALESCE(steam_games.lifecycle_status, 'ACTIVE') <> 'ACTIVE'
                      OR steam_games.steam_last_modified IS DISTINCT FROM EXCLUDED.steam_last_modified
                      OR steam_games.steam_price_change_number IS DISTINCT FROM EXCLUDED.steam_price_change_number
                    THEN 'PENDING' ELSE steam_games.metadata_status END,
                steam_price_change_number = EXCLUDED.steam_price_change_number,
                lifecycle_status = 'ACTIVE',
                last_seen_at = EXCLUDED.last_seen_at,
                reconciliation_generation = COALESCE(EXCLUDED.reconciliation_generation,
                                                     steam_games.reconciliation_generation),
                game_catalog_eligible = COALESCE(EXCLUDED.game_catalog_eligible,
                                                 steam_games.game_catalog_eligible)
            """;

    private final JdbcTemplate jdbc;
    private final SteamGameRepository games;
    private final IgdbRawTaxonomyPersistenceService rawTaxonomy;
    private final GameTagService gameTags;

    @Autowired
    public SteamCatalogPersistenceService(JdbcTemplate jdbc, SteamGameRepository games,
            IgdbRawTaxonomyPersistenceService rawTaxonomy, GameTagService gameTags) {
        this.jdbc = jdbc;
        this.games = games;
        this.rawTaxonomy = rawTaxonomy;
        this.gameTags = gameTags;
    }

    public SteamCatalogPersistenceService(JdbcTemplate jdbc, SteamGameRepository games) {
        this.jdbc = jdbc;
        this.games = games;
        this.rawTaxonomy = null;
        this.gameTags = null;
    }

    @Transactional
    public List<SteamGame> upsertAll(Collection<SteamCatalogClient.CatalogItem> items) {
        return upsertAll(items, null, null);
    }

    @Transactional
    public List<SteamGame> upsertAll(Collection<SteamCatalogClient.CatalogItem> items,
            String reconciliationGeneration) {
        return upsertAll(items, reconciliationGeneration, null);
    }

    @Transactional
    public List<SteamGame> upsertGameCatalogAll(
            Collection<SteamCatalogClient.CatalogItem> items) {
        return upsertAll(items, null, true);
    }

    private List<SteamGame> upsertAll(Collection<SteamCatalogClient.CatalogItem> items,
            String reconciliationGeneration, Boolean gameCatalogEligible) {
        Map<Long, SteamCatalogClient.CatalogItem> uniqueItems = new LinkedHashMap<>();
        items.forEach(item -> uniqueItems.put(item.appId(), item));
        if (uniqueItems.isEmpty()) return List.of();

        List<Object> parameters = new ArrayList<>(uniqueItems.size() * 6);
        uniqueItems.values().forEach(item -> {
            parameters.add(item.appId());
            parameters.add(item.name());
            parameters.add(item.lastModified());
            parameters.add(item.priceChangeNumber());
            parameters.add(reconciliationGeneration);
            parameters.add(gameCatalogEligible);
        });
        int affectedRows = jdbc.update(upsertSql(uniqueItems.size()), parameters.toArray());
        if (affectedRows != uniqueItems.size()) {
            throw new IllegalStateException("Steam catalog bulk UPSERT row count mismatch: expected="
                    + uniqueItems.size() + " actual=" + affectedRows);
        }
        return games.findBySteamAppIdIn(uniqueItems.keySet());
    }

    static String upsertSql(int rowCount) {
        if (rowCount < 1) throw new IllegalArgumentException("rowCount must be positive");
        return INSERT_PREFIX
                + String.join(",\n", java.util.Collections.nCopies(
                        rowCount, "(?, ?, 'game', ?, ?, 'UNKNOWN', false, 'ACTIVE', CURRENT_TIMESTAMP, ?, ?)"))
                + "\n" + UPSERT_SUFFIX;
    }

    @Transactional
    public List<SteamGame> applyIgdbResults(
            Collection<Long> appIds,
            Map<Long, Optional<IgdbEnrichmentClient.IgdbData>> results) {
        long totalStarted = System.nanoTime();
        java.util.Set<Long> requestedAppIds = new java.util.LinkedHashSet<>(appIds);
        Map<Long, SteamGame> uniqueTargets = new LinkedHashMap<>();
        long reloadStarted = System.nanoTime();
        games.findBySteamAppIdIn(requestedAppIds).stream()
                .filter(game -> requestedAppIds.contains(game.getSteamAppId()))
                .forEach(game -> uniqueTargets.putIfAbsent(game.getSteamAppId(), game));
        List<SteamGame> targets = List.copyOf(uniqueTargets.values());
        logStage("steam_game_reload", reloadStarted, targets.size());
        Map<SteamGame, List<IgdbTaxonomyValue>> rawByGame = new LinkedHashMap<>();
        long playerUpdateStarted = System.nanoTime();
        for (SteamGame game : targets) {
            var value = results.getOrDefault(game.getSteamAppId(), Optional.empty());
            if (value.isPresent()) {
                var data = value.get();
                game.updateIgdb(data.gameId(), data.minPlayers(), data.maxPlayers(),
                        data.onlineMax(), data.coopMax(), data.multiplayer(),
                        data.onlineCoop(), data.offlineCoop());
                rawByGame.put(game, data.taxonomyTerms());
            } else {
                game.markIgdbNotFound();
                rawByGame.put(game, List.of());
            }
        }
        logStage("player_field_update", playerUpdateStarted, targets.size());
        if (rawTaxonomy != null && gameTags != null) {
            long rawStarted = System.nanoTime();
            rawTaxonomy.syncBatch(rawByGame);
            logStage("igdb_raw_taxonomy_persistence", rawStarted, targets.size());
            long tagStarted = System.nanoTime();
            Map<SteamGame, List<IgdbTaxonomyValue>> tagValues = new LinkedHashMap<>();
            for (SteamGame game : targets) tagValues.put(game,
                    results.getOrDefault(game.getSteamAppId(), Optional.empty())
                            .map(IgdbEnrichmentClient.IgdbData::taxonomyTerms).orElse(null));
            gameTags.rebuildWithIgdbBatch(tagValues);
            logStage("game_tag_rebuild", tagStarted, targets.size());
        }
        long methodWorkMs = elapsedMs(totalStarted);
        log.info("game_finder_igdb_persistence_timing stage=method_work count={} durationMs={} "
                        + "steamGameSaveCalls=0 tagRebuildBatchCalls={} flushMode=transaction_commit",
                targets.size(), methodWorkMs,
                rawTaxonomy != null && gameTags != null ? 1 : 0);
        registerCommitTiming(targets.size(), System.nanoTime(), totalStarted);
        return targets;
    }

    private static void registerCommitTiming(int count, long workCompletedNanos,
            long totalStartedNanos) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            log.info("game_finder_igdb_persistence_timing stage=entity_save_flush_commit count={} "
                    + "durationMs=unavailable reason=no_transaction_synchronization", count);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                log.info("game_finder_igdb_persistence_timing stage=entity_save_flush_commit count={} "
                                + "durationMs={} totalDurationMs={} transactionStatus={}",
                        count, elapsedMs(workCompletedNanos), elapsedMs(totalStartedNanos), status);
            }
        });
    }

    private static void logStage(String stage, long startedNanos, int count) {
        log.info("game_finder_igdb_persistence_timing stage={} count={} durationMs={}",
                stage, count, elapsedMs(startedNanos));
    }

    private static long elapsedMs(long startedNanos) {
        return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedNanos);
    }

    @Transactional
    public List<SteamGame> markIgdbBatchFailure(
            Collection<Long> appIds, boolean retryable) {
        List<SteamGame> targets = games.findBySteamAppIdIn(appIds);
        targets.forEach(game -> game.markIgdbFailure(retryable));
        return targets;
    }
}
