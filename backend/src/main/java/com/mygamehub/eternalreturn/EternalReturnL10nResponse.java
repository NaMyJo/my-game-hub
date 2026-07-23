package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnL10nResponse(
        Integer code,
        String message,
        Data data
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(
            String l10Path
    ) {
    }
}