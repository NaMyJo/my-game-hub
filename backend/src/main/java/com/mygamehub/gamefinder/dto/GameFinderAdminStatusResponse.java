package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.GameFinderAdminStatusProjection;

public record GameFinderAdminStatusResponse(
        long total,
        long active,
        long unavailable,
        long removed,
        EnrichmentCounts metadata,
        EnrichmentCounts igdb
) {
    public static GameFinderAdminStatusResponse from(GameFinderAdminStatusProjection value) {
        return new GameFinderAdminStatusResponse(
                value.getTotal(), value.getActive(), value.getUnavailable(), value.getRemoved(),
                new EnrichmentCounts(value.getMetadataPending(), value.getMetadataSuccess(),
                        value.getMetadataNotFound(), value.getMetadataRetryableFailure(),
                        value.getMetadataPermanentFailure()),
                new EnrichmentCounts(value.getIgdbPending(), value.getIgdbSuccess(),
                        value.getIgdbNotFound(), value.getIgdbRetryableFailure(),
                        value.getIgdbPermanentFailure()));
    }

    public record EnrichmentCounts(
            long pending,
            long success,
            long notFound,
            long retryableFailure,
            long permanentFailure
    ) {}
}
