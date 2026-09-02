package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class GameFinderAdminAuthorizer {
    private final Set<String> allowedUids;

    public GameFinderAdminAuthorizer(
            @Value("${app.game-finder.admin-uids:}") String configuredUids) {
        this.allowedUids = Arrays.stream(configuredUids.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String uid) {
        return uid != null && allowedUids.contains(uid);
    }
}
