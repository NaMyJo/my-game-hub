package com.mygamehub.riot;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RiotAccount(
        String puuid,
        String gameName,
        String tagLine
) {
}