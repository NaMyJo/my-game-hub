package com.mygamehub.game.dto;

public record FavoriteCharacterResponse(
        Integer characterCode,
        String name,
        Integer totalGames,
        Double averageRank
) {
}