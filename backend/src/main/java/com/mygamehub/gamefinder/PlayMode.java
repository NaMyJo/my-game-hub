package com.mygamehub.gamefinder;

public enum PlayMode {
    SINGLE, MULTI;

    public static PlayMode legacyIfNull(PlayMode value) { return value; }
}
