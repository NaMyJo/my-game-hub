package com.mygamehub.auth;

public record AuthenticatedUser(
        String uid,
        String email,
        String name,
        String picture,
        Long authTimeEpochSeconds
) {
    public AuthenticatedUser(
            String uid,
            String email,
            String name,
            String picture
    ) {
        this(uid, email, name, picture, null);
    }
}
