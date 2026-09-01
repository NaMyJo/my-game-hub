package com.mygamehub.gamefinder.dto;

import java.time.LocalDate;
import java.util.List;

public record GameFinderGameResponse(
        Long steamAppId,
        String name,
        String headerImageUrl,
        String shortDescription,
        Integer currentPrice,
        Integer originalPrice,
        Integer discountPercent,
        String currency,
        Boolean isFree,
        LocalDate releaseDate,
        String releaseDateText,
        boolean comingSoon,
        Integer requiredAge,
        Boolean singlePlayer,
        Boolean multiplayer,
        Boolean onlineCoop,
        Integer minPlayers,
        Integer maxPlayers,
        String lifecycleStatus,
        List<String> canonicalTags,
        String storeUrl
) {}
