package com.mygamehub.gameidentity.dto;

import java.util.List;

public record GameIdentityPreviewResponse(
        String displayName,
        Double averageTopPercent,
        String evaluationType,
        String evaluationMessage,
        Integer includedGameCount,
        List<GameIdentityPreviewEntryResponse> games
) {
}