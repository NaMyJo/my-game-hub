package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.GameFinderAdminStatusProjection;
import java.time.Instant;

public record GameFinderAdminStatusResponse(
        long total,
        long active,
        long unavailable,
        long removed,
        EnrichmentCounts metadata,
        EnrichmentCounts igdb,
        long remainingCandidates,
        long gameCatalogCount,
        long gameCount,
        long nonGameCount,
        long unclassifiedCount,
        Checkpoint checkpoint,
        FullCatalogSync fullCatalogSync,
        FullCatalogSync gameOnlyCatalogSync
) {
    public static GameFinderAdminStatusResponse from(
            GameFinderAdminStatusProjection value, Checkpoint checkpoint,
            FullCatalogSync fullCatalogSync, FullCatalogSync gameOnlyCatalogSync,
            long remainingCandidates) {
        return new GameFinderAdminStatusResponse(
                value.getTotal(), value.getActive(), value.getUnavailable(), value.getRemoved(),
                new EnrichmentCounts(value.getMetadataPending(), value.getMetadataSuccess(),
                        value.getMetadataNotFound(), value.getMetadataRetryableFailure(),
                        value.getMetadataPermanentFailure()),
                new EnrichmentCounts(value.getIgdbPending(), value.getIgdbSuccess(),
                        value.getIgdbNotFound(), value.getIgdbRetryableFailure(),
                        value.getIgdbPermanentFailure()), remainingCandidates,
                value.getGameCatalogCount(),
                value.getGameCount(), value.getNonGameCount(), value.getUnclassifiedCount(),
                checkpoint, fullCatalogSync, gameOnlyCatalogSync);
    }

    public record EnrichmentCounts(
            long pending,
            long success,
            long notFound,
            long retryableFailure,
            long permanentFailure
    ) {}

    public record Checkpoint(
            Long lastAppId,
            Instant lastSuccessfulSyncAt,
            String status,
            boolean hasFailure
    ) {}

    public record FullCatalogSync(
            String status,
            Long lastAppId,
            long discoveredCount,
            Instant lastSuccessfulRunAt,
            boolean completed,
            boolean hasFailure
    ) {}
}
