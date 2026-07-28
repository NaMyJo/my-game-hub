package com.mygamehub.pubg;

public record PubgRankedStats(
        String currentTier,
        String currentSubTier,
        int currentRankPoint,
        int roundsPlayed,
        int kills,
        double damageDealt,
        double kdr
) {

    public double averageDamage() {
        if (roundsPlayed <= 0) {
            return 0;
        }

        return damageDealt / roundsPlayed;
    }
}