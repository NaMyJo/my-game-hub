package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnSeason(
        Integer seasonID,
        String seasonName,
        String seasonStart,
        String seasonEnd,
        Integer isCurrent
) {
}