package com.mygamehub.gamefinder;

public enum ReleasePreference {
    BALANCED,
    RECENT,
    ANY;

    public static ReleasePreference defaultIfNull(ReleasePreference value) {
        return value == null ? RECENT : value;
    }
}
