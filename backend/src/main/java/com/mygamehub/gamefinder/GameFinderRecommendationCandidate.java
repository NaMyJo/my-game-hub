package com.mygamehub.gamefinder;

import java.time.LocalDate;

public record GameFinderRecommendationCandidate(
        Long steamAppId,
        String name,
        String headerImageUrl,
        Integer priceCurrent,
        Integer priceOriginal,
        Integer discountPercent,
        String priceCurrency,
        Boolean isFree,
        LocalDate releaseDate,
        String releaseDateText,
        boolean comingSoon,
        Boolean singlePlayer,
        Boolean multiplayer,
        Boolean onlineCoop,
        Integer maxPlayers,
        String genres) {
}
