package com.mygamehub.user.dto;

import com.mygamehub.user.AppUser;

public record UserProfileResponse(
        String nickname,
        String introduction
) {
    public static UserProfileResponse from(AppUser user) {
        String nickname = user.getProfileNickname();

        if (nickname == null || nickname.isBlank()) {
            nickname = user.getDisplayName();
        }

        if (nickname == null || nickname.isBlank()) {
            nickname = "게이머";
        }

        String introduction = user.getProfileIntroduction();

        if (introduction == null || introduction.isBlank()) {
            introduction = "게임을 사랑하는 게이머";
        }

        return new UserProfileResponse(nickname, introduction);
    }
}
