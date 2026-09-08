package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.IgdbIntegrityProjection;

public record GameFinderAdminIgdbVerifyResponse(
        long totalChecked,
        long valid,
        long successMissingGameId,
        long notFoundWithGameId,
        long invalidPlayerRange,
        long duplicateIgdbMapping,
        long duplicateTaxonomyRelation,
        long durationMs) {
    public static GameFinderAdminIgdbVerifyResponse from(
            IgdbIntegrityProjection value, long durationMs) {
        long invalid = value.getSuccessMissingGameId() + value.getNotFoundWithGameId()
                + value.getInvalidPlayerRange() + value.getDuplicateTaxonomyRelation();
        return new GameFinderAdminIgdbVerifyResponse(value.getTotalChecked(),
                Math.max(0, value.getTotalChecked() - invalid),
                value.getSuccessMissingGameId(), value.getNotFoundWithGameId(),
                value.getInvalidPlayerRange(), value.getDuplicateIgdbMapping(),
                value.getDuplicateTaxonomyRelation(), durationMs);
    }
}
