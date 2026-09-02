package com.mygamehub.gamefinder.dto;

import jakarta.validation.constraints.NotNull;

public record GameFinderAdminCatalogExpandRequest(@NotNull Integer targetTotal) {
    public boolean supportedTarget() {
        return targetTotal != null && (targetTotal == 500 || targetTotal == 1000
                || targetTotal == 5000 || targetTotal == 10000);
    }
}
