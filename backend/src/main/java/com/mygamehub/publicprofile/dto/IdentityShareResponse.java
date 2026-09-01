package com.mygamehub.publicprofile.dto;

public record IdentityShareResponse(
        String shareId,
        boolean enabled,
        String identityNumber,
        String displayName,
        String issuedDate,
        Double gamePowerPercent,
        String evaluationMessage,
        String snapshotJson
) {}
