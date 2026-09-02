package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderAdminStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameFinderAdminStatusService {
    private final SteamGameRepository games;
    private final CatalogSyncCheckpointRepository checkpoints;

    public GameFinderAdminStatusService(
            SteamGameRepository games, CatalogSyncCheckpointRepository checkpoints) {
        this.games = games;
        this.checkpoints = checkpoints;
    }

    @Transactional(readOnly = true)
    public GameFinderAdminStatusResponse status() {
        var checkpoint = checkpoints.findById(SteamCatalogSyncService.ADMIN_EXPAND_CHECKPOINT_KEY)
                .map(value -> new GameFinderAdminStatusResponse.Checkpoint(
                        value.getLastAppId(), value.getLastSuccessfulSyncAt(), value.getStatus(),
                        value.getFailureInfo() != null && !value.getFailureInfo().isBlank()))
                .orElseGet(() -> new GameFinderAdminStatusResponse.Checkpoint(
                        0L, null, "NEW", false));
        var fullSync = checkpoints.findById(SteamCatalogSyncService.ADMIN_FULL_SYNC_CHECKPOINT_KEY)
                .map(value -> new GameFinderAdminStatusResponse.FullCatalogSync(
                        value.getStatus(), value.getLastAppId(),
                        value.getProcessedCount() == null ? 0 : value.getProcessedCount(),
                        value.getLastSuccessfulSyncAt(), "COMPLETED".equals(value.getStatus()),
                        value.getFailureInfo() != null && !value.getFailureInfo().isBlank()))
                .orElseGet(() -> new GameFinderAdminStatusResponse.FullCatalogSync(
                        "NEW", 0L, 0, null, false, false));
        return GameFinderAdminStatusResponse.from(games.adminStatus(), checkpoint, fullSync);
    }
}
