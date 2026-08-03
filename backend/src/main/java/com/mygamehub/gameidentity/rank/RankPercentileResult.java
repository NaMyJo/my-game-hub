package com.mygamehub.gameidentity.rank;

import com.mygamehub.game.GameType;

public record RankPercentileResult(
        GameType gameType,
        String originalRank,
        String normalizedTier,
        String normalizedDivision,
        Double topPercent,
        String season,
        String region,
        boolean available,
        boolean estimated
) {

    public static RankPercentileResult unavailable(
            GameType gameType,
            String originalRank
    ) {
        return new RankPercentileResult(
                gameType,
                originalRank,
                null,
                null,
                null,
                null,
                null,
                false,
                false
        );
    }
}