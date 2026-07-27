package com.mygamehub.maplestory;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MapleStoryStatResponse(

        @JsonProperty("final_stat")
        List<FinalStat> finalStat

) {

    public record FinalStat(

            @JsonProperty("stat_name")
            String statName,

            @JsonProperty("stat_value")
            String statValue
    ) {
    }

    public String findStatValue(String name) {
        if (finalStat == null) {
            return null;
        }

        return finalStat.stream()
                .filter(stat -> name.equals(stat.statName()))
                .map(FinalStat::statValue)
                .findFirst()
                .orElse(null);
    }
}