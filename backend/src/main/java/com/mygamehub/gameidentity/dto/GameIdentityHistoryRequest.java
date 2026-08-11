package com.mygamehub.gameidentity.dto;

public record GameIdentityHistoryRequest(
        String identityNumber,
        String displayName,
        String issuedDate,
        Double gamePowerPercent,
        String evaluationMessage,
        String snapshotJson
) {
}