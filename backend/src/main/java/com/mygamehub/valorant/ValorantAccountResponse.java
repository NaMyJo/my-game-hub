package com.mygamehub.valorant;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ValorantAccountResponse(
        Integer status,
        Data data
) {

    public record Data(
            String puuid,
            String region,

            @JsonProperty("account_level")
            Integer accountLevel,

            String name,
            String tag,
            Card card
    ) {
    }

    public record Card(
            String small,
            String large,
            String wide,
            String id
    ) {
    }
}