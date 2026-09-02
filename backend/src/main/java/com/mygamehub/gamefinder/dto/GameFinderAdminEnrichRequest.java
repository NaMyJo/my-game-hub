package com.mygamehub.gamefinder.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record GameFinderAdminEnrichRequest(
        @Min(1) @Max(40) Integer batchSize
) {
    public int effectiveBatchSize() {
        return batchSize == null ? 1 : batchSize;
    }
}
