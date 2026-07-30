package com.mygamehub.overwatch;

public class OverwatchPageChangedException extends RuntimeException {

    public OverwatchPageChangedException() {
        super(
                "오버워치 웹페이지 개편으로 현재 데이터 수집이 불가능합니다. " +
                "서비스 업데이트 후 다시 이용해주세요."
        );
    }

    public OverwatchPageChangedException(Throwable cause) {
        super(
                "오버워치 웹페이지 개편으로 현재 데이터 수집이 불가능합니다. " +
                "서비스 업데이트 후 다시 이용해주세요.",
                cause
        );
    }
}