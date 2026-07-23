package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnCharacterStat(
        Integer characterCode,
        Integer totalGames,
        Integer usages,
        Integer maxKillings,
        Integer top3,
        Integer wins,
        Integer mostUsedSkinCode,
        Integer latestUsedSkinCode,
        Double top3Rate,
        Double averageRank
) {
}