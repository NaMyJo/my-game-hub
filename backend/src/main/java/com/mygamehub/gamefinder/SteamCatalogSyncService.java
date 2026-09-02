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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

@Service
public class SteamCatalogSyncService {
    private static final Logger log = LoggerFactory.getLogger(SteamCatalogSyncService.class);
    private static final String CHECKPOINT_KEY = "steam-catalog";
    static final String ADMIN_EXPAND_CHECKPOINT_KEY = "steam-catalog-admin-expand";
    static final String ADMIN_FULL_SYNC_CHECKPOINT_KEY = "steam-catalog-admin-full-sync";
    static final String ADMIN_GAME_ONLY_CHECKPOINT_KEY = "steam-catalog-admin-game-only";
    static final int ADMIN_EXPAND_MAX_APPS_PER_REQUEST = 500;
    private final SteamCatalogClient catalog;
    private final SteamStoreDetailClient store;
    private final SteamGameRepository games;
    private final SteamCatalogPersistenceService persistence;
    private final CatalogSyncCheckpointRepository checkpoints;
    private final IgdbEnrichmentClient igdb;
    private final GameTagService tagService;
    private final int batchSize;
    private final int pagesPerRun;
    private final int bootstrapMaxApps;
    private final long storeDelayMs;
    private final int metadataConcurrency;
    private final long igdbIntervalMs;
    private final long metadataRetryCooldownMs;
    private final int metadata429AbortThreshold;
    private final SteamStoreRequestPolicy storeRequestPolicy;

    @Autowired
    public SteamCatalogSyncService(SteamCatalogClient catalog, SteamStoreDetailClient store,
            SteamGameRepository games, SteamCatalogPersistenceService persistence,
            CatalogSyncCheckpointRepository checkpoints, IgdbEnrichmentClient igdb, GameTagService tagService,
            @Value("${app.game-finder.sync-batch-size:40}") int batchSize,
            @Value("${app.game-finder.catalog-pages-per-run:4}") int pagesPerRun,
            @Value("${app.game-finder.bootstrap-max-apps:0}") int bootstrapMaxApps,
            @Value("${app.game-finder.steam-store-request-delay-ms:500}") long storeDelayMs,
            @Value("${app.game-finder.steam-metadata-concurrency:1}") int metadataConcurrency,
            @Value("${app.game-finder.igdb-request-interval-ms:260}") long igdbIntervalMs,
            @Value("${app.game-finder.metadata-retry-cooldown-ms:1200000}") long metadataRetryCooldownMs,
            @Value("${app.game-finder.metadata-429-abort-threshold:3}") int metadata429AbortThreshold,
            SteamStoreRequestPolicy storeRequestPolicy) {
        this.catalog = catalog;
        this.store = store;
        this.games = games;
        this.persistence = persistence;
        this.checkpoints = checkpoints;
        this.igdb = igdb;
        this.tagService = tagService;
        this.batchSize = batchSize;
        this.pagesPerRun = pagesPerRun;
        this.bootstrapMaxApps = Math.max(0, bootstrapMaxApps);
        this.storeDelayMs = storeDelayMs;
        this.metadataConcurrency = Math.max(1, Math.min(4, metadataConcurrency));
        this.igdbIntervalMs = igdbIntervalMs;
        this.metadataRetryCooldownMs = Math.max(0, metadataRetryCooldownMs);
        this.metadata429AbortThreshold = Math.max(1, metadata429AbortThreshold);
        this.storeRequestPolicy = storeRequestPolicy;
    }

    SteamCatalogSyncService(SteamCatalogClient catalog, SteamStoreDetailClient store,
            SteamGameRepository games, SteamCatalogPersistenceService persistence,
            CatalogSyncCheckpointRepository checkpoints, IgdbEnrichmentClient igdb,
            GameTagService tagService, int batchSize, int pagesPerRun, int bootstrapMaxApps,
            long storeDelayMs, long igdbIntervalMs) {
        this(catalog, store, games, persistence, checkpoints, igdb, tagService, batchSize,
                pagesPerRun, bootstrapMaxApps, storeDelayMs, 1, igdbIntervalMs,
                1_200_000, 3, new SteamStoreRequestPolicy(0, 2, 1, 2, millis -> {}));
    }

    SteamCatalogSyncService(SteamCatalogClient catalog, SteamStoreDetailClient store,
            SteamGameRepository games, SteamCatalogPersistenceService persistence,
            CatalogSyncCheckpointRepository checkpoints, IgdbEnrichmentClient igdb,
            GameTagService tagService, int batchSize, int pagesPerRun, int bootstrapMaxApps,
            long storeDelayMs, int metadataConcurrency, long igdbIntervalMs) {
        this(catalog, store, games, persistence, checkpoints, igdb, tagService, batchSize,
                pagesPerRun, bootstrapMaxApps, storeDelayMs, metadataConcurrency, igdbIntervalMs,
                1_200_000, 3, new SteamStoreRequestPolicy(0, 2, 1, 2, millis -> {}));
    }

    @PostConstruct
    void logConfiguration() {
        log.info("game_finder_bootstrap_config catalogPageSize={} pagesPerRun={} storeDelayMs={} "
                        + "metadataConcurrency={} metadataRetryCooldownMs={} storeMaxRetries={} "
                        + "storeInitialMaxRetries={} storeRateLimitCooldownMs={} "
                        + "metadata429AbortThreshold={} igdbMaxRps={} batchSize={} bootstrapMaxApps={}",
                catalog.pageSize(), pagesPerRun, storeDelayMs, metadataConcurrency,
                metadataRetryCooldownMs, storeRequestPolicy.maxRetries(),
                storeRequestPolicy.initialMaxRetries(), storeRequestPolicy.rateLimitCooldownMs(),
                metadata429AbortThreshold,
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

    public void reconcile() {
        while (reconcilePage()) {}
    }

    public boolean reconcilePage() {
        String key = "steam-reconciliation";
        CatalogSyncCheckpoint cp = checkpoints.findById(key)
                .orElseGet(() -> new CatalogSyncCheckpoint(key));
        cp.running();
        String generation = cp.ensureReconciliationGeneration();
        checkpoints.save(cp);
        try {
            long start = cp.getLastAppId() == null ? 0 : cp.getLastAppId();
            SteamCatalogClient.CatalogPage page = catalog.page(start, null);
            logPage(page, catalog.pageSize(), start);
            persistCatalog(page.items(), generation);
            if (page.hasMore()) {
                cp.page(page.lastAppId(), 0);
            } else {
                int removed = games.markMissingAsRemoved(generation);
                log.info("game_finder_reconciliation_complete generation={} removed={}",
                        generation, removed);
                cp.success(0);
                cp.clearReconciliationGeneration();
            }
            checkpoints.save(cp);
            return page.hasMore();
        } catch (RuntimeException exception) {
            cp.failed(exception.getMessage());
            checkpoints.save(cp);
            throw exception;
        }
    }

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
        return enrichBatch(batchSize).processed();
    }

    public synchronized EnrichmentBatchResult enrichBatch(int requestedBatchSize) {
        LinkedHashMap<Long, SteamGame> targets = new LinkedHashMap<>();
        games.findMetadataCandidates(staleBefore(), retryBefore(), PageRequest.of(0, requestedBatchSize))
                .forEach(game -> targets.put(game.getSteamAppId(), game));
        if (targets.size() < requestedBatchSize) {
            games.findIgdbCandidates(PageRequest.of(0, requestedBatchSize - targets.size()))
                    .forEach(game -> targets.put(game.getSteamAppId(), game));
        }
        log.info("game_finder_enrichment_start candidateCount={}", targets.size());
        for (SteamGame game : targets.values()) enrichOne(game);
        boolean hasMoreCandidates = hasEnrichmentCandidates();
        return EnrichmentBatchResult.from(targets.values(), hasMoreCandidates);
    }

    public synchronized EnrichmentStageBatchResult enrichMetadataBatch(int requestedBatchSize) {
        LinkedHashMap<Long, SteamGame> uniqueTargets = new LinkedHashMap<>();
        games.findMetadataCandidates(staleBefore(), retryBefore(),
                        PageRequest.of(0, requestedBatchSize))
                .forEach(game -> uniqueTargets.putIfAbsent(game.getSteamAppId(), game));
        List<SteamGame> targets = List.copyOf(uniqueTargets.values());
        log.info("game_finder_metadata_enrichment_start candidateCount={}", targets.size());
        SteamStoreRequestPolicy.Stats before = storeRequestPolicy.stats();
        MetadataBatchExecution execution = enrichMetadataTargets(targets);
        SteamStoreRequestPolicy.Stats stats = storeRequestPolicy.stats().minus(before);
        log.info("game_finder_metadata_batch_http_stats apps={} attempts={} retries={} "
                        + "http429={} http5xx={} network={} parsing={} averageAttemptsPerApp={}",
                stats.executions(), stats.attempts(), stats.retries(), stats.http429(),
                stats.http5xx(), stats.network(), stats.parsing(), stats.averageAttemptsPerApp());
        if (execution.aborted()) {
            log.warn("game_finder_metadata_batch_aborted reason=rate_limited attempted={} pending={}",
                    execution.attempted().size(), targets.size() - execution.attempted().size());
        }
        return EnrichmentStageBatchResult.from(
                execution.attempted(), true,
                games.countMetadataCandidates(staleBefore(), retryBefore()) > 0,
                execution.rateLimited());
    }

    private MetadataBatchExecution enrichMetadataTargets(List<SteamGame> targets) {
        MetadataRateLimitGuard guard = new MetadataRateLimitGuard(metadata429AbortThreshold);
        List<SteamGame> attempted = new java.util.concurrent.CopyOnWriteArrayList<>();
        if (metadataConcurrency == 1 || targets.size() < 2) {
            for (SteamGame game : targets) {
                if (guard.aborted()) break;
                MetadataAttemptOutcome outcome = enrichMetadataOne(game);
                attempted.add(game);
                guard.record(outcome);
            }
            return new MetadataBatchExecution(
                    List.copyOf(attempted), guard.rateLimited(), guard.aborted());
        }
        try (var executor = Executors.newFixedThreadPool(
                Math.min(metadataConcurrency, targets.size()))) {
            var futures = targets.stream()
                    .map(game -> executor.submit(() -> {
                        if (guard.aborted()) return;
                        MetadataAttemptOutcome outcome = enrichMetadataOne(game);
                        attempted.add(game);
                        guard.record(outcome);
                    }))
                    .toList();
            for (var future : futures) {
                try {
                    future.get();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Steam metadata batch was interrupted", exception);
                } catch (ExecutionException exception) {
                    throw new IllegalStateException("Steam metadata worker failed",
                            exception.getCause());
                }
            }
        }
        return new MetadataBatchExecution(
                List.copyOf(attempted), guard.rateLimited(), guard.aborted());
    }

    public int metadataConcurrency() { return metadataConcurrency; }
    public long storeRequestDelayMs() { return storeDelayMs; }

    public synchronized EnrichmentStageBatchResult enrichIgdbBatch(int requestedBatchSize) {
        List<SteamGame> targets = games.findIgdbCandidates(PageRequest.of(0, requestedBatchSize));
        log.info("game_finder_igdb_enrichment_start candidateCount={}", targets.size());
        if (!targets.isEmpty() && igdb.configured()) {
            List<Long> appIds = targets.stream().map(SteamGame::getSteamAppId).toList();
            try {
                var values = igdb.findBySteamAppIds(appIds);
                targets = persistence.applyIgdbResults(appIds, values);
                targets.forEach(tagService::rebuild);
            } catch (RuntimeException exception) {
                boolean retryable = retryable(exception);
                targets = persistence.markIgdbBatchFailure(appIds, retryable);
                log.warn("game_finder_igdb_batch_failed count={} errorType={}",
                        targets.size(), exception.getClass().getSimpleName());
            }
        }
        return EnrichmentStageBatchResult.from(
                targets, false, igdb.configured() && games.countIgdbCandidates() > 0, false);
    }

    public boolean hasEnrichmentCandidates() {
        return remainingEnrichmentCandidates() > 0;
    }

    public long remainingEnrichmentCandidates() {
        Instant staleBefore = Instant.now().minus(Duration.ofDays(7));
        return igdb.configured()
                ? games.countEnrichmentCandidates(staleBefore, retryBefore())
                : games.countMetadataCandidates(staleBefore, retryBefore());
    }

    public long remainingMetadataCandidates() {
        return games.countMetadataCandidates(staleBefore(), retryBefore());
    }

    public long remainingIgdbCandidates() {
        return igdb.configured() ? games.countIgdbCandidates() : 0;
    }

    public CatalogExpandResult expandCatalogTo(int targetTotal) {
        long before = games.count();
        if (before >= targetTotal) {
            return new CatalogExpandResult(0, 0, 0, before, targetTotal, true);
        }
        int requested = (int) Math.min(ADMIN_EXPAND_MAX_APPS_PER_REQUEST,
                targetTotal - before);
        CatalogSyncCheckpoint cp = checkpoints.findById(ADMIN_EXPAND_CHECKPOINT_KEY)
                .orElseGet(() -> new CatalogSyncCheckpoint(ADMIN_EXPAND_CHECKPOINT_KEY));
        cp.running();
        checkpoints.save(cp);
        try {
            long start = cp.getLastAppId() == null ? 0 : cp.getLastAppId();
            SteamCatalogClient.CatalogPage page = catalog.page(start, null, requested);
            logPage(page, requested, start);
            List<SteamGame> saved = persistCatalog(page.items());
            long after = games.count();
            if (!page.items().isEmpty()) cp.progress(page.lastAppId());
            checkpoints.save(cp);
            return new CatalogExpandResult(page.items().size(), saved.size(),
                    Math.max(0, after - before), after, targetTotal, after >= targetTotal);
        } catch (RuntimeException exception) {
            cp.failed(exception.getClass().getSimpleName());
            checkpoints.save(cp);
            throw exception;
        }
    }

    public FullCatalogSyncResult syncFullCatalogPage() {
        CatalogSyncCheckpoint cp = checkpoints.findById(ADMIN_FULL_SYNC_CHECKPOINT_KEY)
                .orElseGet(() -> new CatalogSyncCheckpoint(ADMIN_FULL_SYNC_CHECKPOINT_KEY));
        long currentTotal = games.count();
        if ("COMPLETED".equals(cp.getStatus())) {
            return new FullCatalogSyncResult(0, 0, currentTotal,
                    cp.getLastAppId() == null ? 0 : cp.getLastAppId(),
                    cp.getProcessedCount() == null ? 0 : cp.getProcessedCount(), true);
        }
        cp.running();
        checkpoints.save(cp);
        try {
            long start = cp.getLastAppId() == null ? 0 : cp.getLastAppId();
            SteamCatalogClient.CatalogPage page = catalog.pageAllApps(
                    start, ADMIN_EXPAND_MAX_APPS_PER_REQUEST);
            logPage(page, ADMIN_EXPAND_MAX_APPS_PER_REQUEST, start);
            if (page.hasMore() && (page.items().isEmpty() || page.lastAppId() <= start)) {
                throw new IllegalStateException("Steam full catalog page made no cursor progress");
            }
            persistCatalog(page.items());
            long after = games.count();
            boolean completed = !page.hasMore();
            long nextAppId = page.items().isEmpty() ? start : page.lastAppId();
            cp.fullSyncPage(nextAppId, page.items().size(), completed);
            checkpoints.save(cp);
            return new FullCatalogSyncResult(page.items().size(),
                    Math.max(0, after - currentTotal), after, nextAppId,
                    cp.getProcessedCount(), completed);
        } catch (RuntimeException exception) {
            cp.failed(exception.getClass().getSimpleName());
            checkpoints.save(cp);
            throw exception;
        }
    }

    public GameOnlyCatalogSyncResult syncGameOnlyCatalogPage() {
        CatalogSyncCheckpoint cp = checkpoints.findById(ADMIN_GAME_ONLY_CHECKPOINT_KEY)
                .orElseGet(() -> new CatalogSyncCheckpoint(ADMIN_GAME_ONLY_CHECKPOINT_KEY));
        long eligibleTotal = games.countByGameCatalogEligibleTrue();
        if ("COMPLETED".equals(cp.getStatus())) {
            return new GameOnlyCatalogSyncResult(0, eligibleTotal,
                    cp.getLastAppId() == null ? 0 : cp.getLastAppId(),
                    cp.getProcessedCount() == null ? 0 : cp.getProcessedCount(), true);
        }
        cp.running();
        checkpoints.save(cp);
        try {
            long start = cp.getLastAppId() == null ? 0 : cp.getLastAppId();
            SteamCatalogClient.CatalogPage page = catalog.page(
                    start, null, ADMIN_EXPAND_MAX_APPS_PER_REQUEST);
            logPage(page, ADMIN_EXPAND_MAX_APPS_PER_REQUEST, start);
            if (page.hasMore() && (page.items().isEmpty() || page.lastAppId() <= start)) {
                throw new IllegalStateException("Steam game-only catalog page made no cursor progress");
            }
            persistence.upsertGameCatalogAll(page.items());
            boolean completed = !page.hasMore();
            long nextAppId = page.items().isEmpty() ? start : page.lastAppId();
            cp.fullSyncPage(nextAppId, page.items().size(), completed);
            checkpoints.save(cp);
            return new GameOnlyCatalogSyncResult(page.items().size(),
                    games.countByGameCatalogEligibleTrue(), nextAppId,
                    cp.getProcessedCount(), completed);
        } catch (RuntimeException exception) {
            cp.failed(exception.getClass().getSimpleName());
            checkpoints.save(cp);
            throw exception;
        }
    }

    public int taxonomyBatch() {
        List<SteamGame> targets = games.findTaxonomyCandidates(PageRequest.of(0, batchSize));
        targets.forEach(tagService::rebuild);
        log.info("game_finder_taxonomy_batch_complete processed={}", targets.size());
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
        return persistCatalog(items, null);
    }

    private List<SteamGame> persistCatalog(List<SteamCatalogClient.CatalogItem> items,
            String reconciliationGeneration) {
        long startedAt = System.nanoTime();
        log.info("game_finder_catalog_persist_start count={}", items.size());
        List<SteamGame> saved = reconciliationGeneration == null
                ? persistence.upsertAll(items)
                : persistence.upsertAll(items, reconciliationGeneration);
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
        boolean metadataTerminal = game.getMetadataStatus() == EnrichmentStatus.NOT_FOUND
                || game.getMetadataStatus() == EnrichmentStatus.PERMANENT_FAILURE;
        boolean metadataCurrent = metadataTerminal || (game.getMetadataUpdatedAt() != null
                && game.getMetadataUpdatedAt().isAfter(Instant.now().minus(Duration.ofDays(7)))
                && (game.getMetadataStatus() == null
                    || game.getMetadataStatus() == EnrichmentStatus.SUCCESS));
        if (metadataCurrent && game.getMetadataStatus() == null
                && game.getMetadataUpdatedAt() != null) {
            game.normalizeLegacyMetadataStatus();
            games.save(game);
        }
        if (!metadataCurrent) {
            try {
                var detail = store.get(game.getSteamAppId());
                if (detail.isEmpty()) {
                    game.markMetadataNotFound();
                    games.save(game);
                    return new EnrichmentResult(false, false);
                }
                var value = detail.get();
                game.updateStoreDetail(value.name(), value.type(), value.image(), value.description(), value.free(),
                        value.currency(), value.original(), value.current(), value.discount(),
                        value.requiredAge(), value.adult(), value.releaseDate(), value.releaseText(),
                        value.comingSoon(), value.earlyAccess(), value.genres(), value.categories(),
                        value.single(), value.multiplayer(), value.onlineCoop(), value.offlineCoop());
                games.save(game);
                tagService.rebuild(game);
                steamEnriched = true;
            } catch (RuntimeException exception) {
                game.markMetadataFailure(retryable(exception));
                games.save(game);
                log.warn("game_finder_metadata_failed appId={} errorType={}",
                        game.getSteamAppId(), exception.getClass().getSimpleName());
                return new EnrichmentResult(false, false);
            }
        }
        if (game.getIgdbStatus() == null && game.getIgdbUpdatedAt() != null) {
            game.normalizeLegacyIgdbStatus();
            games.save(game);
        }
        if (game.getStoreType() != null && !"game".equalsIgnoreCase(game.getStoreType())) {
            if (game.getIgdbStatus() != EnrichmentStatus.NOT_FOUND
                    && game.getIgdbStatus() != EnrichmentStatus.PERMANENT_FAILURE) {
                game.markIgdbNotApplicable();
                games.save(game);
            }
            return new EnrichmentResult(steamEnriched, false);
        }
        boolean igdbComplete = game.getIgdbStatus() == EnrichmentStatus.SUCCESS
                || game.getIgdbStatus() == EnrichmentStatus.NOT_FOUND
                || game.getIgdbStatus() == EnrichmentStatus.PERMANENT_FAILURE
                || (game.getIgdbStatus() == null && game.getIgdbUpdatedAt() != null);
        if (!igdbComplete && igdb.configured()) {
            try {
                igdbProcessed = true;
                var result = igdb.findBySteamAppId(game.getSteamAppId());
                if (result.isPresent()) {
                    var data = result.get();
                    game.updateIgdb(data.gameId(), data.minPlayers(), data.maxPlayers(),
                            data.onlineMax(), data.coopMax(), data.multiplayer(),
                            data.onlineCoop(), data.offlineCoop());
                } else {
                    game.markIgdbNotFound();
                }
                games.save(game);
            } catch (RuntimeException exception) {
                game.markIgdbFailure(retryable(exception));
                games.save(game);
                log.warn("game_finder_igdb_failed appId={} errorType={}",
                        game.getSteamAppId(), exception.getClass().getSimpleName());
            }
        }
        return new EnrichmentResult(steamEnriched, igdbProcessed);
    }

    private MetadataAttemptOutcome enrichMetadataOne(SteamGame game) {
        try {
            boolean initialAttempt = game.getMetadataUpdatedAt() == null
                    && (game.getMetadataStatus() == null
                        || game.getMetadataStatus() == EnrichmentStatus.PENDING);
            var detail = store.get(game.getSteamAppId(), initialAttempt);
            if (detail.isEmpty()) {
                game.markMetadataNotFound();
                games.save(game);
                return MetadataAttemptOutcome.COMPLETED;
            }
            var value = detail.get();
            game.updateStoreDetail(value.name(), value.type(), value.image(), value.description(), value.free(),
                    value.currency(), value.original(), value.current(), value.discount(),
                    value.requiredAge(), value.adult(), value.releaseDate(), value.releaseText(),
                    value.comingSoon(), value.earlyAccess(), value.genres(), value.categories(),
                    value.single(), value.multiplayer(), value.onlineCoop(), value.offlineCoop());
            if (game.getStoreType() != null && !"game".equalsIgnoreCase(game.getStoreType())) {
                game.markIgdbNotApplicable();
            }
            games.save(game);
            return MetadataAttemptOutcome.COMPLETED;
        } catch (RuntimeException exception) {
            game.markMetadataFailure(retryable(exception));
            games.save(game);
            log.warn("game_finder_metadata_failed appId={} reason={} errorType={}",
                    game.getSteamAppId(), SteamStoreRequestPolicy.failureCategory(exception),
                    exception.getClass().getSimpleName());
            return "HTTP_429".equals(SteamStoreRequestPolicy.failureCategory(exception))
                    ? MetadataAttemptOutcome.RATE_LIMITED
                    : MetadataAttemptOutcome.OTHER_FAILURE;
        }
    }

    private enum MetadataAttemptOutcome { COMPLETED, RATE_LIMITED, OTHER_FAILURE }

    private record MetadataBatchExecution(
            List<SteamGame> attempted, boolean rateLimited, boolean aborted) {}

    private static final class MetadataRateLimitGuard {
        private final int threshold;
        private int consecutive429;
        private boolean rateLimited;
        private boolean aborted;

        private MetadataRateLimitGuard(int threshold) { this.threshold = threshold; }

        synchronized void record(MetadataAttemptOutcome outcome) {
            if (outcome == MetadataAttemptOutcome.RATE_LIMITED) {
                rateLimited = true;
                if (++consecutive429 >= threshold) aborted = true;
            } else {
                consecutive429 = 0;
            }
        }

        synchronized boolean aborted() { return aborted; }
        synchronized boolean rateLimited() { return rateLimited; }
    }

    private Instant staleBefore() { return Instant.now().minus(Duration.ofDays(7)); }
    private Instant retryBefore() { return Instant.now().minusMillis(metadataRetryCooldownMs); }

    private boolean retryable(RuntimeException exception) {
        if (exception instanceof ExternalApiRetry.RetryableFailure failure) return failure.isRetryable();
        if (exception instanceof org.springframework.web.client.HttpStatusCodeException http) {
            int status = http.getStatusCode().value();
            return status == 429 || status >= 500;
        }
        return true;
    }

    private record EnrichmentResult(boolean steamEnriched, boolean igdbProcessed) {}

    public record CatalogExpandResult(
            int fetched,
            int upserted,
            long newlySaved,
            long currentTotal,
            int targetTotal,
            boolean targetReached) {}

    public record FullCatalogSyncResult(
            int fetched,
            long newlySaved,
            long currentCatalogTotal,
            long lastAppId,
            long discoveredCount,
            boolean completed) {}

    public record GameOnlyCatalogSyncResult(
            int fetched,
            long eligibleCatalogTotal,
            long lastAppId,
            long discoveredCount,
            boolean completed) {}

    public record EnrichmentBatchResult(
            int processed,
            int metadataSuccess,
            int metadataNotFound,
            int metadataRetryableFailure,
            int metadataPermanentFailure,
            int igdbSuccess,
            int igdbNotFound,
            int igdbRetryableFailure,
            int igdbPermanentFailure,
            boolean hasMoreCandidates) {
        static EnrichmentBatchResult from(
                java.util.Collection<SteamGame> games, boolean hasMoreCandidates) {
            return new EnrichmentBatchResult(
                    games.size(),
                    count(games, true, EnrichmentStatus.SUCCESS),
                    count(games, true, EnrichmentStatus.NOT_FOUND),
                    count(games, true, EnrichmentStatus.RETRYABLE_FAILURE),
                    count(games, true, EnrichmentStatus.PERMANENT_FAILURE),
                    count(games, false, EnrichmentStatus.SUCCESS),
                    count(games, false, EnrichmentStatus.NOT_FOUND),
                    count(games, false, EnrichmentStatus.RETRYABLE_FAILURE),
                    count(games, false, EnrichmentStatus.PERMANENT_FAILURE),
                    hasMoreCandidates);
        }

        private static int count(java.util.Collection<SteamGame> games, boolean metadata,
                EnrichmentStatus status) {
            return (int) games.stream().filter(game -> status == (metadata
                    ? game.getMetadataStatus() : game.getIgdbStatus())).count();
        }
    }

    public record EnrichmentStageBatchResult(
            int processed,
            int success,
            int notFound,
            int retryableFailure,
            int permanentFailure,
            boolean hasMoreCandidates,
            boolean rateLimited) {
        static EnrichmentStageBatchResult from(
                java.util.Collection<SteamGame> games, boolean metadata,
                boolean hasMoreCandidates, boolean rateLimited) {
            return new EnrichmentStageBatchResult(
                    games.size(),
                    EnrichmentBatchResult.count(games, metadata, EnrichmentStatus.SUCCESS),
                    EnrichmentBatchResult.count(games, metadata, EnrichmentStatus.NOT_FOUND),
                    EnrichmentBatchResult.count(games, metadata, EnrichmentStatus.RETRYABLE_FAILURE),
                    EnrichmentBatchResult.count(games, metadata, EnrichmentStatus.PERMANENT_FAILURE),
                    hasMoreCandidates,
                    rateLimited);
        }
    }
}
