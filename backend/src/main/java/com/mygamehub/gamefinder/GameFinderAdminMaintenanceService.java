package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
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
    private final AtomicBoolean enrichmentRunning = new AtomicBoolean(false);

    public GameFinderAdminMaintenanceService(SteamCatalogSyncService syncService) {
        this.syncService = syncService;
    }

    public Optional<GameFinderAdminEnrichResponse> tryEnrich(int batchSize) {
        if (!enrichmentRunning.compareAndSet(false, true)) {
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
            enrichmentRunning.set(false);
        }
    }
}
