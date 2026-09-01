package com.mygamehub.gamefinder.dto;
import java.time.LocalDate;
import java.util.List;
public record GameFinderRecommendationResponse(
        Long steamAppId, String name, String headerImageUrl, int matchScore,
        Integer currentPrice, Integer originalPrice, Integer discountPercent,
        String currency, Boolean isFree, LocalDate releaseDate,
        String releaseDateText, boolean comingSoon, Boolean singlePlayer,
        Boolean multiplayer, Boolean onlineCoop, Integer maxPlayers,
        List<String> genres, String storeUrl
) {}
