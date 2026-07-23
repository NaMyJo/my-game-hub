package com.mygamehub.auth;

public record AuthenticatedUser(
        String uid,
        String email,
        String name,
        String picture
) {
}
