package com.mygamehub.publicprofile.dto;

public record PublicIdentityResponse(
        String shareId,
        String identityNumber,
        String displayName,
        String issuedDate,
        Double gamePowerPercent,
        String evaluationMessage,
        String snapshotJson
) {}
