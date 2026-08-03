package com.mygamehub.gameidentity.dto;

import com.mygamehub.gameidentity.GameIdentityCard;
import com.mygamehub.gameidentity.GameIdentityEvaluationType;

import java.time.Instant;
import java.util.List;

public record GameIdentityResponse(
        Long id,
        String displayName,
        Double averageTopPercent,
        GameIdentityEvaluationType evaluationType,
        String evaluationMessage,
        String imageUrl,
        Instant createdAt,
        List<GameIdentityEntryResponse> games
) {

    public static GameIdentityResponse from(
            GameIdentityCard card
    ) {
        return new GameIdentityResponse(
                card.getId(),
                card.getDisplayName(),
                card.getAverageTopPercent(),
                card.getEvaluationType(),
                card.getEvaluationMessage(),
                card.getImageUrl(),
                card.getCreatedAt(),
                card.getEntries()
                        .stream()
                        .map(GameIdentityEntryResponse::from)
                        .toList()
        );
    }
}