package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminCatalogExpandResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminFullCatalogSyncResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminGameCatalogSyncResponse;
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
    private final AtomicBoolean maintenanceRunning = new AtomicBoolean(false);

    public GameFinderAdminMaintenanceService(SteamCatalogSyncService syncService) {
        this.syncService = syncService;
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
