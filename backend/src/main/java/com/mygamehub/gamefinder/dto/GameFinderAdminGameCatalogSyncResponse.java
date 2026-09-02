package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamCatalogSyncService.GameOnlyCatalogSyncResult;

public record GameFinderAdminGameCatalogSyncResponse(
        int fetched,
        long eligibleCatalogTotal,
        long lastAppId,
        long discoveredCount,
        boolean completed,
        long durationMs) {
    public static GameFinderAdminGameCatalogSyncResponse from(
            GameOnlyCatalogSyncResult result, long durationMs) {
        return new GameFinderAdminGameCatalogSyncResponse(
                result.fetched(), result.eligibleCatalogTotal(), result.lastAppId(),
                result.discoveredCount(), result.completed(), durationMs);
    }
}
