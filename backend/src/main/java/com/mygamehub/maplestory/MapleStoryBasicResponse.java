package com.mygamehub.maplestory;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MapleStoryBasicResponse(

        @JsonProperty("character_name")
        String characterName,

        @JsonProperty("world_name")
        String worldName,

        @JsonProperty("character_class")
        String characterClass,

        @JsonProperty("character_level")
        Integer characterLevel,

        @JsonProperty("character_image")
        String characterImage
) {
}