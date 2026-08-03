package com.mygamehub.gameidentity.rank;

public record GamePowerEvaluation(
        Double averageTopPercent,
        String message,
        int includedGameCount,
        boolean estimated
) {
}