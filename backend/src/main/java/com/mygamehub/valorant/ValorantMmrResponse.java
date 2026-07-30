package com.mygamehub.valorant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ValorantMmrResponse(
        Integer status,
        Data data
) {

    public record Data(
            Account account,
            Peak peak,
            Current current
    ) {
    }

    public record Account(
            String puuid,
            String name,
            String tag
    ) {
    }

    public record Peak(
            Tier tier
    ) {
    }

    public record Current(
            Tier tier,
            Integer rr,

            @JsonProperty("last_change")
            Integer lastChange,

            Integer elo,

            @JsonProperty("games_needed_for_rating")
            Integer gamesNeededForRating
    ) {
    }

    public record Tier(
            Integer id,
            String name
    ) {
    }
}