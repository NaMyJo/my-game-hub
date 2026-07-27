package com.mygamehub.maplestory;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MapleStoryOcidResponse(
        @JsonProperty("ocid")
        String ocid
) {
}