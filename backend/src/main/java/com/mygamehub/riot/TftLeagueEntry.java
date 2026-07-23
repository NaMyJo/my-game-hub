package com.mygamehub.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TftLeagueEntry(
        String queueType,
        String tier,
        String rank,
        Integer leaguePoints,
        Integer wins,
        Integer losses
) {
}