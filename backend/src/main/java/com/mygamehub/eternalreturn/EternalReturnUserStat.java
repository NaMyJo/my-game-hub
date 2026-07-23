package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnUserStat(
        Integer seasonId,
        Integer matchingMode,
        Integer matchingTeamMode,

        Integer mmr,
        Integer rank,
        Integer rankSize,

        Integer totalGames,
        Integer totalWins,
        Integer totalTeamKills,
        Integer totalDeaths,
        Integer escapeCount,

        Double rankPercent,
        Double averageRank,
        Double averageKills,
        Double averageAssistants,
        Double averageHunts,

        Double top1,
        Double top2,
        Double top3,
        Double top5,
        Double top7,

        List<EternalReturnCharacterStat> characterStats
) {
}