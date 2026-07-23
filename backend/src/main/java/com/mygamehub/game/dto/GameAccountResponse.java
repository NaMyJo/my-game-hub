package com.mygamehub.game.dto;

import com.mygamehub.game.GameAccount;
import com.mygamehub.game.GameType;

import java.time.Instant;

public record GameAccountResponse(
        Long id,
        GameType gameType,
        String accountName,

        String primaryLabel,
        String primaryValue,

        String secondaryLabel,
        String secondaryValue,

        String tertiaryLabel,
        String tertiaryValue,

        Integer totalGames,
        Double averagePlacement,

        FavoriteCharacterResponse favoriteCharacter1,
        FavoriteCharacterResponse favoriteCharacter2,
        FavoriteCharacterResponse favoriteCharacter3,

        Instant updatedAt
) {

    public static GameAccountResponse from(GameAccount entity) {

        FavoriteCharacterResponse favorite1 =
        createFavoriteCharacter(
                entity.getFavoriteCharacter1Code(),
                entity.getFavoriteCharacter1Name(),
                entity.getFavoriteCharacter1Games(),
                entity.getFavoriteCharacter1AverageRank()
        );

FavoriteCharacterResponse favorite2 =
        createFavoriteCharacter(
                entity.getFavoriteCharacter2Code(),
                entity.getFavoriteCharacter2Name(),
                entity.getFavoriteCharacter2Games(),
                entity.getFavoriteCharacter2AverageRank()
        );

FavoriteCharacterResponse favorite3 =
        createFavoriteCharacter(
                entity.getFavoriteCharacter3Code(),
                entity.getFavoriteCharacter3Name(),
                entity.getFavoriteCharacter3Games(),
                entity.getFavoriteCharacter3AverageRank()
        );
        return new GameAccountResponse(
                entity.getId(),
                entity.getGameType(),
                entity.getAccountName(),

                entity.getPrimaryLabel(),
                entity.getPrimaryValue(),

                entity.getSecondaryLabel(),
                entity.getSecondaryValue(),

                entity.getTertiaryLabel(),
                entity.getTertiaryValue(),

                entity.getTotalGames(),
                entity.getAveragePlacement(),

                favorite1,
                favorite2,
                favorite3,

                entity.getUpdatedAt()
        );
    }
private static FavoriteCharacterResponse createFavoriteCharacter(
        Integer code,
        String name,
        Integer games,
        Double averageRank
) {
    if (code == null) {
        return null;
    }

    return new FavoriteCharacterResponse(
            code,
            name,
            games,
            averageRank
    );
}
}