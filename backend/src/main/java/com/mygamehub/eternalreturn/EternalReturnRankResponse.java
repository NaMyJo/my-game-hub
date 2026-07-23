package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnRankResponse(
        Integer code,
        String message,
        EternalReturnRank userRank
) {
}