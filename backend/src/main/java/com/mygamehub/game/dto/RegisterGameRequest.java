package com.mygamehub.game.dto;

import com.mygamehub.game.GameType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RegisterGameRequest(
        @NotNull GameType gameType,
        @NotBlank String accountName
) {
}
