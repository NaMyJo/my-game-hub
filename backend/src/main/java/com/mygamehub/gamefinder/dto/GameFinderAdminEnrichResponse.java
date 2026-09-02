package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamCatalogSyncService.EnrichmentBatchResult;

public record GameFinderAdminEnrichResponse(
        int requestedBatchSize,
        int processed,
        int metadataSuccess,
        int metadataNotFound,
        int metadataRetryableFailure,
        int metadataPermanentFailure,
        int igdbSuccess,
        int igdbNotFound,
        int igdbRetryableFailure,
        int igdbPermanentFailure,
        long durationMs
) {
    public static GameFinderAdminEnrichResponse from(
            int requestedBatchSize, EnrichmentBatchResult result, long durationMs) {
        return new GameFinderAdminEnrichResponse(
                requestedBatchSize, result.processed(), result.metadataSuccess(),
                result.metadataNotFound(), result.metadataRetryableFailure(),
                result.metadataPermanentFailure(), result.igdbSuccess(), result.igdbNotFound(),
                result.igdbRetryableFailure(), result.igdbPermanentFailure(), durationMs);
    }
}
