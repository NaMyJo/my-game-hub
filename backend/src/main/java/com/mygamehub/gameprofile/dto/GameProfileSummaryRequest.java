package com.mygamehub.gameprofile.dto;

public record GameProfileSummaryRequest(
        String identityNickname,
        Double gamePowerPercent,
        Integer reflectedGameCount,
        String evaluationMessage
) {
}