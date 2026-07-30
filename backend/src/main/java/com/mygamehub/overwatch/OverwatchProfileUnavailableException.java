package com.mygamehub.overwatch;

public class OverwatchProfileUnavailableException extends RuntimeException {

    public OverwatchProfileUnavailableException() {
        super(
                "오버워치 공개 프로필을 찾을 수 없습니다. " +
                "BattleTag와 프로필 공개 설정을 확인해주세요."
        );
    }
}