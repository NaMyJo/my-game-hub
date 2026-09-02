package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.SteamMetadataVerificationService.VerificationResult;
import com.mygamehub.gamefinder.SteamMetadataVerificationService.VerificationSummary;

import java.util.List;

public record GameFinderAdminMetadataVerifyResponse(
        int sampled,
        int matched,
        int changed,
        int criticalMismatch,
        int storeUnavailable,
        int verificationError,
        long durationMs,
        List<Detail> criticalDetails) {
    public static GameFinderAdminMetadataVerifyResponse from(
            VerificationSummary summary, long durationMs) {
        List<Detail> details = summary.results().stream()
                .filter(value -> value.outcome()
                        == com.mygamehub.gamefinder.SteamMetadataVerificationService.VerificationOutcome.CRITICAL)
                .map(Detail::from).toList();
        return new GameFinderAdminMetadataVerifyResponse(summary.sampled(), summary.matched(),
                summary.changed(), summary.criticalMismatch(), summary.storeUnavailable(),
                summary.verificationError(), durationMs, details);
    }

    public record Detail(long steamAppId, String dbName, Long responseAppId,
            String responseName, List<String> mismatchedFields) {
        static Detail from(VerificationResult value) {
            return new Detail(value.steamAppId(), value.databaseName(),
                    value.responseSteamAppId(), value.responseName(), value.mismatchedFields());
        }
    }
}
