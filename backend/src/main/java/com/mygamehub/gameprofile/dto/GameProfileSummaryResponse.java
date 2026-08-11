package com.mygamehub.gameprofile.dto;

import com.mygamehub.gameprofile.GameProfileSummary;

import java.time.Instant;

public record GameProfileSummaryResponse(
        Long id,
        String identityNickname,
        Double gamePowerPercent,
        Integer reflectedGameCount,
        String evaluationMessage,
        Instant updatedAt
) {

    public static GameProfileSummaryResponse from(
            GameProfileSummary profile
    ) {
        return new GameProfileSummaryResponse(
                profile.getId(),
                profile.getIdentityNickname(),
                profile.getGamePowerPercent(),
                profile.getReflectedGameCount(),
                profile.getEvaluationMessage(),
                profile.getUpdatedAt()
        );
    }
}