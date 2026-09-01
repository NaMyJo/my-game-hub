package com.mygamehub.gamefinder;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

@Service
public class SteamCatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(SteamCatalogSyncService.class);
    private static final String CHECKPOINT_KEY = "steam-catalog";
    private final SteamCatalogClient catalog;
    private final SteamStoreDetailClient store;
    private final SteamGameRepository games;
    private final SteamCatalogPersistenceService persistence;
    private final CatalogSyncCheckpointRepository checkpoints;
    private final IgdbEnrichmentClient igdb;
    private final int batchSize;
    private final int pagesPerRun;
    private final int bootstrapMaxApps;
    private final long storeDelayMs;
    private final long igdbIntervalMs;

    @Autowired
    public SteamCatalogSyncService(SteamCatalogClient catalog, SteamStoreDetailClient store,
            SteamGameRepository games, SteamCatalogPersistenceService persistence,
            CatalogSyncCheckpointRepository checkpoints, IgdbEnrichmentClient igdb,
            @Value("${app.game-finder.sync-batch-size:40}") int batchSize,
            @Value("${app.game-finder.catalog-pages-per-run:4}") int pagesPerRun,
            @Value("${app.game-finder.bootstrap-max-apps:0}") int bootstrapMaxApps,
            @Value("${app.game-finder.steam-store-request-delay-ms:500}") long storeDelayMs,
            @Value("${app.game-finder.igdb-request-interval-ms:260}") long igdbIntervalMs) {
        this.catalog = catalog;
        this.store = store;
        this.games = games;
        this.persistence = persistence;
        this.checkpoints = checkpoints;
        this.igdb = igdb;
        this.batchSize = batchSize;
        this.pagesPerRun = pagesPerRun;
        this.bootstrapMaxApps = Math.max(0, bootstrapMaxApps);
        this.storeDelayMs = storeDelayMs;
        this.igdbIntervalMs = igdbIntervalMs;
    }

    @PostConstruct
    void logConfiguration() {
        log.info("game_finder_bootstrap_config catalogPageSize={} pagesPerRun={} storeDelayMs={} "
                        + "igdbMaxRps={} batchSize={} bootstrapMaxApps={}",
                catalog.pageSize(), pagesPerRun, storeDelayMs,
                igdbIntervalMs <= 0 ? "unlimited"
                        : String.format(Locale.ROOT, "%.2f", 1000.0 / igdbIntervalMs),
                batchSize, bootstrapMaxApps);
    }

    public void syncIncremental() {
        for (int i = 0; i < pagesPerRun && sync(CHECKPOINT_KEY); i++) {}
        enrichBatch();
    }

    public void bootstrap() {
        if (bootstrapMaxApps > 0) {
            bootstrapLimited(bootstrapMaxApps);
            return;
        }
        while (sync(CHECKPOINT_KEY)) enrichBatch();
        while (enrichBatch() > 0) {}
    }

    public void dryRun() {
        CatalogSyncCheckpoint cp = checkpoint();
        log.info("game_finder_bootstrap_dry_run startLastAppId={} modifiedSince={} "
                        + "catalogPageSize={} pagesPerRun={} storeDelayMs={} igdbMaxRps={} "
                        + "batchSize={} bootstrapMaxApps={}",
                cp.getLastAppId(), cp.getLastModifiedSince(), catalog.pageSize(), pagesPerRun,
                storeDelayMs, igdbIntervalMs <= 0 ? "unlimited"
                        : String.format(Locale.ROOT, "%.2f", 1000.0 / igdbIntervalMs),
                batchSize, bootstrapMaxApps);
    }

    public void catalogDiagnostic() { catalog.diagnose(); }

    public int catalogPersistDiagnostic() {
        if (bootstrapMaxApps < 1 || bootstrapMaxApps > 100) {
            throw new IllegalStateException(
                    "catalog-persist-diagnostic requires GAME_FINDER_BOOTSTRAP_MAX_APPS=1..100");
        }
        CatalogSyncCheckpoint cp = checkpoint();
        SteamCatalogClient.CatalogPage page = requestPage(cp, bootstrapMaxApps);
        List<SteamCatalogClient.CatalogItem> items = limitedItems(page.items(), bootstrapMaxApps);
        persistCatalog(items);
        log.info("game_finder_catalog_persist_diagnostic_complete catalogSaved={} "
                        + "checkpointChanged=false enrichmentStarted=false", items.size());
        return items.size();
    }

    public int bootstrapLimited(int maxApps) {
        if (maxApps < 1) return 0;
        CatalogSyncCheckpoint cp = checkpoint();
        SteamCatalogClient.CatalogPage page = requestPage(cp, maxApps);
        List<SteamCatalogClient.CatalogItem> items = limitedItems(page.items(), maxApps);
        List<SteamGame> saved = persistCatalog(items);
        log.info("game_finder_enrichment_start candidateCount={}", saved.size());
        int steamEnriched = 0;
        int igdbProcessed = 0;
        for (SteamGame game : saved) {
            EnrichmentResult result = enrichOne(game);
            if (result.steamEnriched()) steamEnriched++;
            if (result.igdbProcessed()) igdbProcessed++;
        }
        log.info("game_finder_limited_bootstrap_progress catalogSaved={} steamEnriched={} "
                        + "igdbProcessed={} checkpointChanged=false",
                saved.size(), steamEnriched, igdbProcessed);
        return saved.size();
    }

    public boolean sync(String key) {
        CatalogSyncCheckpoint cp = checkpoints.findById(key)
                .orElseGet(() -> new CatalogSyncCheckpoint(key));
        cp.running();
        checkpoints.save(cp);
        try {
            long start = cp.getLastAppId() == null ? 0 : cp.getLastAppId();
            SteamCatalogClient.CatalogPage page = catalog.page(start, cp.getLastModifiedSince());
            logPage(page, catalog.pageSize(), start);
            persistCatalog(page.items());
            long maxModified = cp.getPendingMaxModified() == null
                    ? (cp.getLastModifiedSince() == null ? 0 : cp.getLastModifiedSince())
                    : cp.getPendingMaxModified();
            for (SteamCatalogClient.CatalogItem item : page.items()) {
                maxModified = Math.max(maxModified, item.lastModified());
            }
            if (page.hasMore()) cp.page(page.lastAppId(), maxModified);
            else cp.success(maxModified);
            checkpoints.save(cp);
            log.info("game_finder_catalog_page key={} count={} more={} lastAppId={}",
                    key, page.items().size(), page.hasMore(), page.lastAppId());
            return page.hasMore();
        } catch (RuntimeException exception) {
            cp.failed(exception.getMessage());
            checkpoints.save(cp);
            log.error("game_finder_catalog_sync_failed key={}", key, exception);
            throw exception;
        }
    }

    public int enrichBatch() {
        LinkedHashMap<Long, SteamGame> targets = new LinkedHashMap<>();
        games.findByPriceUpdatedAtIsNull(PageRequest.of(0, batchSize))
                .forEach(game -> targets.put(game.getSteamAppId(), game));
        if (targets.size() < batchSize) {
            games.findByMetadataUpdatedAtIsNullOrMetadataUpdatedAtBefore(
                            Instant.now().minus(Duration.ofDays(7)),
                            PageRequest.of(0, batchSize - targets.size()))
                    .forEach(game -> targets.put(game.getSteamAppId(), game));
        }
        log.info("game_finder_enrichment_start candidateCount={}", targets.size());
        for (SteamGame game : targets.values()) enrichOne(game);
        return targets.size();
    }

    private SteamCatalogClient.CatalogPage requestPage(CatalogSyncCheckpoint cp, int requested) {
        long start = cp.getLastAppId() == null ? 0 : cp.getLastAppId();
        SteamCatalogClient.CatalogPage page = catalog.page(
                start, cp.getLastModifiedSince(), requested);
        logPage(page, requested, start);
        return page;
    }

    private void logPage(SteamCatalogClient.CatalogPage page, int requested, long startAppId) {
        long endAppId = page.items().isEmpty() ? startAppId
                : page.items().get(page.items().size() - 1).appId();
        log.info("game_finder_catalog_page_received requested={} returned={} startAppId={} endAppId={}",
                requested, page.items().size(), startAppId, endAppId);
    }

    private List<SteamCatalogClient.CatalogItem> limitedItems(
            List<SteamCatalogClient.CatalogItem> items, int limit) {
        return items.stream().limit(limit).toList();
    }

    private List<SteamGame> persistCatalog(List<SteamCatalogClient.CatalogItem> items) {
        long startedAt = System.nanoTime();
        log.info("game_finder_catalog_persist_start count={}", items.size());
        List<SteamGame> saved = persistence.upsertAll(items);
        long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
        log.info("game_finder_catalog_persist_complete count={} durationMs={}",
                saved.size(), durationMs);
        return saved;
    }

    private CatalogSyncCheckpoint checkpoint() {
        return checkpoints.findById(CHECKPOINT_KEY)
                .orElseGet(() -> new CatalogSyncCheckpoint(CHECKPOINT_KEY));
    }

    private EnrichmentResult enrichOne(SteamGame game) {
        boolean steamEnriched = false;
        boolean igdbProcessed = false;
        try {
            var detail = store.get(game.getSteamAppId());
            if (detail.isEmpty()) {
                game.markEnrichmentAttempted();
                games.save(game);
                return new EnrichmentResult(false, false);
            }
            var value = detail.get();
            game.updateStoreDetail(value.type(), value.image(), value.description(), value.free(),
                    value.currency(), value.original(), value.current(), value.discount(),
                    value.requiredAge(), value.adult(), value.releaseDate(), value.releaseText(),
                    value.comingSoon(), value.earlyAccess(), value.genres(), value.categories(),
                    value.single(), value.multiplayer(), value.onlineCoop(), value.offlineCoop());
            games.save(game);
            steamEnriched = true;
            if (igdb.configured() && game.getIgdbGameId() == null) {
                igdbProcessed = true;
                igdb.findBySteamAppId(game.getSteamAppId()).ifPresent(data -> {
                    game.updateIgdb(data.gameId(), data.minPlayers(), data.maxPlayers(),
                            data.onlineMax(), data.coopMax(), data.multiplayer(),
                            data.onlineCoop(), data.offlineCoop());
                    games.save(game);
                });
            }
            return new EnrichmentResult(steamEnriched, igdbProcessed);
        } catch (RuntimeException exception) {
            game.markEnrichmentAttempted();
            games.save(game);
            log.warn("game_finder_enrichment_failed appId={} errorType={} message={}",
                    game.getSteamAppId(), exception.getClass().getSimpleName(),
                    exception.getMessage());
            return new EnrichmentResult(steamEnriched, igdbProcessed);
        }
    }

    private record EnrichmentResult(boolean steamEnriched, boolean igdbProcessed) {}
}
