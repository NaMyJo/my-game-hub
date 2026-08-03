package com.mygamehub.gameidentity.dto;

import com.mygamehub.game.GameType;
import com.mygamehub.gameidentity.GameIdentityEntry;

public record GameIdentityEntryResponse(
        Long id,
        Long gameAccountId,
        GameType gameType,
        String accountName,
        String metricLabel,
        String metricValue,
        Double topPercent,
        boolean includedInAverage,
        Integer displayOrder
) {

    public static GameIdentityEntryResponse from(
            GameIdentityEntry entry
    ) {
        return new GameIdentityEntryResponse(
                entry.getId(),
                entry.getGameAccountId(),
                entry.getGameType(),
                entry.getAccountName(),
                entry.getMetricLabel(),
                entry.getMetricValue(),
                entry.getTopPercent(),
                entry.isIncludedInAverage(),
                entry.getDisplayOrder()
        );
    }
}