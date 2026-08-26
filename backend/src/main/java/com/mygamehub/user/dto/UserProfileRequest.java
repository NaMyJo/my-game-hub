package com.mygamehub.user.dto;

public record UserProfileRequest(
        String nickname,
        String introduction
) {
}
