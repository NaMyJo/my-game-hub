package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamCatalogSyncService.EnrichmentStageBatchResult;

public record GameFinderAdminStageEnrichResponse(
        String stage,
        int requestedBatchSize,
        int processed,
        int success,
        int notFound,
        int retryableFailure,
        int permanentFailure,
        boolean hasMoreCandidates,
        long durationMs) {
    public static GameFinderAdminStageEnrichResponse from(
            String stage, int requestedBatchSize,
            EnrichmentStageBatchResult result, long durationMs) {
        return new GameFinderAdminStageEnrichResponse(stage, requestedBatchSize,
                result.processed(), result.success(), result.notFound(),
                result.retryableFailure(), result.permanentFailure(),
                result.hasMoreCandidates(), durationMs);
    }
}
