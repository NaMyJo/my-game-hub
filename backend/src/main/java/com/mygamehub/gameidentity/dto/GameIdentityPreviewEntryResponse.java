package com.mygamehub.gameidentity.dto;

import com.mygamehub.game.GameType;

public record GameIdentityPreviewEntryResponse(
        Long gameAccountId,
        GameType gameType,
        String accountName,
        String metricLabel,
        String metricValue,
        Double topPercent,
        boolean includedInAverage,
        boolean estimated
) {
}