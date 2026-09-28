package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamCatalogSyncService.NewGamesCatalogSyncResult;
import java.time.Instant;

public record GameFinderAdminNewGamesSyncResponse(
        int fetched,
        int upserted,
        long newlySaved,
        long currentCatalog,
        long previousLastAppId,
        long currentLastAppId,
        boolean hasMore,
        Instant lastRunAt) {
    public static GameFinderAdminNewGamesSyncResponse from(
            NewGamesCatalogSyncResult result) {
        return new GameFinderAdminNewGamesSyncResponse(
                result.fetched(), result.upserted(), result.newlySaved(),
                result.currentCatalog(), result.previousLastAppId(),
                result.currentLastAppId(), result.hasMore(), result.lastRunAt());
    }
}
