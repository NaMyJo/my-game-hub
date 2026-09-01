package com.mygamehub.publicprofile.dto;

import com.mygamehub.gameidentity.dto.GameIdentityPreviewResponse;

public record PublicProfileResponse(
        String publicId,
        String nickname,
        String introduction,
        GameIdentityPreviewResponse gamePower,
        PublicIdentityResponse latestIdentity
) {}
