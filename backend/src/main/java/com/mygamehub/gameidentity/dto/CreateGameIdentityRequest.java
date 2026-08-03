package com.mygamehub.gameidentity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGameIdentityRequest(

        @NotBlank(
                message = "신분증 닉네임을 입력해주세요."
        )
        @Size(
                max = 12,
                message = "신분증 닉네임은 12자 이하로 입력해주세요."
        )
        String displayName,

        List<Long> gameAccountIds
) {
}