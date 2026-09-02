package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamCatalogSyncService.CatalogExpandResult;

public record GameFinderAdminCatalogExpandResponse(
        int fetched,
        int upserted,
        long newlySaved,
        long currentTotal,
        int targetTotal,
        boolean targetReached,
        long durationMs
) {
    public static GameFinderAdminCatalogExpandResponse from(
            CatalogExpandResult result, long durationMs) {
        return new GameFinderAdminCatalogExpandResponse(
                result.fetched(), result.upserted(), result.newlySaved(),
                result.currentTotal(), result.targetTotal(), result.targetReached(), durationMs);
    }
}
