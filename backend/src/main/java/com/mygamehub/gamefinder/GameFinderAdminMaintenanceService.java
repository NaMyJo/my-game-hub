package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminCatalogExpandResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminFullCatalogSyncResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminGameCatalogSyncResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminStageEnrichResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminMetadataVerifyResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GameFinderAdminMaintenanceService {
    private static final Logger log = LoggerFactory.getLogger(GameFinderAdminMaintenanceService.class);
    private final SteamCatalogSyncService syncService;
    private final SteamMetadataVerificationService metadataVerifier;
    private final AtomicBoolean maintenanceRunning = new AtomicBoolean(false);
    private final AtomicBoolean metadataRunnerOwned = new AtomicBoolean(false);

    public GameFinderAdminMaintenanceService(SteamCatalogSyncService syncService,
            SteamMetadataVerificationService metadataVerifier) {
        this.syncService = syncService;
        this.metadataVerifier = metadataVerifier;
    }

    public Optional<GameFinderAdminMetadataVerifyResponse> tryMetadataVerify(
            int sampleSize, SteamMetadataVerificationService.VerificationMode mode) {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            log.warn("game_finder_admin_metadata_verify_rejected reason=maintenance_running");
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        log.info("game_finder_admin_metadata_verify_start sampleSize={} mode={}", sampleSize, mode);
        try {
            var result = metadataVerifier.verify(sampleSize, mode);
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("game_finder_admin_metadata_verify_complete sampled={} matched={} "
                            + "criticalMismatch={} durationMs={}", result.sampled(),
                    result.matched(), result.criticalMismatch(), durationMs);
            return Optional.of(GameFinderAdminMetadataVerifyResponse.from(result, durationMs));
        } finally {
            maintenanceRunning.set(false);
        }
    }

    public Optional<GameFinderAdminEnrichResponse> tryEnrich(int batchSize) {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            log.warn("game_finder_admin_enrich_rejected reason=already_running");
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        log.info("game_finder_admin_enrich_start batchSize={}", batchSize);
        try {
            var result = syncService.enrichBatch(batchSize);
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("game_finder_admin_enrich_complete batchSize={} processed={} durationMs={}",
                    batchSize, result.processed(), durationMs);
            return Optional.of(GameFinderAdminEnrichResponse.from(batchSize, result, durationMs));
        } finally {
            maintenanceRunning.set(false);
        }
    }

    public Optional<GameFinderAdminStageEnrichResponse> tryMetadataEnrich(int batchSize) {
        if (metadataRunnerOwned.get()) {
            log.warn("game_finder_admin_stage_enrich_rejected stage=metadata reason=runner_active");
            return Optional.empty();
        }
        return tryStageEnrich("metadata", batchSize, true);
    }

    boolean claimMetadataRunner() {
        if (!metadataRunnerOwned.compareAndSet(false, true)) return false;
        if (!maintenanceRunning.compareAndSet(false, true)) {
            metadataRunnerOwned.set(false);
            return false;
        }
        maintenanceRunning.set(false);
        return true;
    }

    void releaseMetadataRunner() { metadataRunnerOwned.set(false); }

    Optional<SteamCatalogSyncService.EnrichmentStageBatchResult> tryMetadataRunnerBatch(
            int batchSize) {
        if (!metadataRunnerOwned.get() || !maintenanceRunning.compareAndSet(false, true)) {
            return Optional.empty();
        }
        try {
            return Optional.of(syncService.enrichInitialMetadataBatch(batchSize));
        } finally {
            maintenanceRunning.set(false);
        }
    }

    boolean metadataRunnerActive() { return metadataRunnerOwned.get(); }

    public Optional<GameFinderAdminStageEnrichResponse> tryIgdbEnrich(int batchSize) {
        return tryStageEnrich("igdb", batchSize, false);
    }

    private Optional<GameFinderAdminStageEnrichResponse> tryStageEnrich(
            String stage, int batchSize, boolean metadata) {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            log.warn("game_finder_admin_stage_enrich_rejected stage={} reason=already_running", stage);
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        log.info("game_finder_admin_stage_enrich_start stage={} batchSize={}", stage, batchSize);
        try {
            var result = metadata
                    ? syncService.enrichMetadataBatch(batchSize)
                    : syncService.enrichIgdbBatch(batchSize);
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("game_finder_admin_stage_enrich_complete stage={} batchSize={} processed={} durationMs={}",
                    stage, batchSize, result.processed(), durationMs);
            return Optional.of(GameFinderAdminStageEnrichResponse.from(
                    stage, batchSize, result, durationMs));
        } finally {
            maintenanceRunning.set(false);
        }
    }

    public Optional<GameFinderAdminCatalogExpandResponse> tryExpandCatalog(int targetTotal) {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            log.warn("game_finder_admin_catalog_rejected reason=maintenance_running");
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        log.info("game_finder_admin_catalog_start targetTotal={} maxApps={}",
                targetTotal, SteamCatalogSyncService.ADMIN_EXPAND_MAX_APPS_PER_REQUEST);
        try {
            var result = syncService.expandCatalogTo(targetTotal);
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("game_finder_admin_catalog_complete targetTotal={} fetched={} newlySaved={} currentTotal={} targetReached={} durationMs={}",
                    targetTotal, result.fetched(), result.newlySaved(), result.currentTotal(),
                    result.targetReached(), durationMs);
            return Optional.of(GameFinderAdminCatalogExpandResponse.from(result, durationMs));
        } finally {
            maintenanceRunning.set(false);
        }
    }

    public Optional<GameFinderAdminFullCatalogSyncResponse> tryFullCatalogSync() {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            log.warn("game_finder_admin_full_catalog_rejected reason=maintenance_running");
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        log.info("game_finder_admin_full_catalog_start maxApps={}",
                SteamCatalogSyncService.ADMIN_EXPAND_MAX_APPS_PER_REQUEST);
        try {
            var result = syncService.syncFullCatalogPage();
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("game_finder_admin_full_catalog_complete fetched={} newlySaved={} currentTotal={} lastAppId={} discoveredCount={} completed={} durationMs={}",
                    result.fetched(), result.newlySaved(), result.currentCatalogTotal(),
                    result.lastAppId(), result.discoveredCount(), result.completed(), durationMs);
            return Optional.of(GameFinderAdminFullCatalogSyncResponse.from(result, durationMs));
        } finally {
            maintenanceRunning.set(false);
        }
    }

    public Optional<GameFinderAdminGameCatalogSyncResponse> tryGameCatalogSync() {
        if (!maintenanceRunning.compareAndSet(false, true)) {
            log.warn("game_finder_admin_game_catalog_rejected reason=maintenance_running");
            return Optional.empty();
        }
        long startedAt = System.nanoTime();
        log.info("game_finder_admin_game_catalog_start maxApps={}",
                SteamCatalogSyncService.ADMIN_EXPAND_MAX_APPS_PER_REQUEST);
        try {
            var result = syncService.syncGameOnlyCatalogPage();
            long durationMs = Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
            log.info("game_finder_admin_game_catalog_complete fetched={} eligibleTotal={} lastAppId={} discoveredCount={} completed={} durationMs={}",
                    result.fetched(), result.eligibleCatalogTotal(), result.lastAppId(),
                    result.discoveredCount(), result.completed(), durationMs);
            return Optional.of(GameFinderAdminGameCatalogSyncResponse.from(result, durationMs));
        } finally {
            maintenanceRunning.set(false);
        }
    }
}
