package com.mygamehub.gameidentity.dto;

import java.time.Instant;

public record GameIdentityHistoryResponse(
        Long id,
        String identityNumber,
        String displayName,
        String issuedDate,
        Double gamePowerPercent,
        String evaluationMessage,
        String snapshotJson,
        Instant updatedAt
) {
}