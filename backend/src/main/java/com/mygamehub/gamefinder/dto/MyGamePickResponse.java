package com.mygamehub.gamefinder.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record MyGamePickResponse(
        Long steamAppId,
        String name,
        String headerImageUrl,
        Integer currentPrice,
        Integer originalPrice,
        Integer discountPercent,
        String currency,
        Boolean isFree,
        LocalDate releaseDate,
        String releaseDateText,
        boolean comingSoon,
        Boolean singlePlayer,
        Boolean multiplayer,
        Boolean onlineCoop,
        Integer minPlayers,
        Integer maxPlayers,
        String playerSummary,
        List<String> canonicalTags,
        String storeUrl,
        Instant pickedAt
) {}
