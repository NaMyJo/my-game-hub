package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnRank(
        Integer serverCode,
        Integer mmr,
        Integer rewardServerCode,
        Integer serverRank,
        String nickname,
        Integer rank
) {
}