package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamCatalogSyncService.FullCatalogSyncResult;

public record GameFinderAdminFullCatalogSyncResponse(
        int fetched,
        long newlySaved,
        long currentCatalogTotal,
        long lastAppId,
        long discoveredCount,
        boolean completed,
        long durationMs
) {
    public static GameFinderAdminFullCatalogSyncResponse from(
            FullCatalogSyncResult result, long durationMs) {
        return new GameFinderAdminFullCatalogSyncResponse(
                result.fetched(), result.newlySaved(), result.currentCatalogTotal(),
                result.lastAppId(), result.discoveredCount(), result.completed(), durationMs);
    }
}
