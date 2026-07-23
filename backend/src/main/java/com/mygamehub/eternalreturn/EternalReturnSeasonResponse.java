package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnSeasonResponse(
        Integer code,
        String message,
        List<EternalReturnSeason> data
) {
}