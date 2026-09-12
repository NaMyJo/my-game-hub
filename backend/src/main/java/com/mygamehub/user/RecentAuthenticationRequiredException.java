package com.mygamehub.user;

public class RecentAuthenticationRequiredException extends RuntimeException {

    public RecentAuthenticationRequiredException() {
        super("보안을 위해 다시 로그인한 뒤 계정 삭제를 시도해주세요.");
    }
}
