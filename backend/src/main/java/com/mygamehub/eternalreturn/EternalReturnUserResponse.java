package com.mygamehub.eternalreturn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record EternalReturnUserResponse(
        Integer code,
        String message,
        EternalReturnUser user
) {
}