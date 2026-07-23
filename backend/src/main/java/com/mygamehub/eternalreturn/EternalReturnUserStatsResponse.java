package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnUserStatsResponse(
        Integer code,
        String message,
        List<EternalReturnUserStat> userStats
) {
}