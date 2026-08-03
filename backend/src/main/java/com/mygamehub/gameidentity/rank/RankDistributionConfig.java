package com.mygamehub.gameidentity.rank;

import java.util.Map;

public record RankDistributionConfig(
        String version,
        Map<String, GameDistribution> games
) {

    public record GameDistribution(
            String season,
            String region,
            Boolean estimated,
            Map<String, Map<String, Double>> tiers
    ) {

        public boolean isEstimated() {
            return Boolean.TRUE.equals(estimated);
        }
    }
}