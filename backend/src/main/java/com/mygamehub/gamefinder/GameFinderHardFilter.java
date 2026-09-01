package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderRecommendRequest;
import org.springframework.stereotype.Component;

@Component
public class GameFinderHardFilter {
    public boolean matches(SteamGame game, GameFinderFilterCriteria request) {
        return game.isDiscoverable()
                && priceMatches(game, request.priceMin(), request.priceMax())
                && adultMatches(game, request.includeAdult())
                && playersMatch(game, request.playerMin(), request.playerMax());
    }
    boolean priceMatches(SteamGame game, int min, int max) {
        boolean unrestricted = min == 0 && max == 100000;
        Integer price = Boolean.TRUE.equals(game.getIsFree())
                ? Integer.valueOf(0)
                : game.getPriceCurrent();
        if (price == null) return unrestricted;
        return price >= min && (max == 100000 || price <= max);
    }
    boolean adultMatches(SteamGame game, boolean includeAdult) {
        return includeAdult || !"ADULT".equals(game.getAdultStatus());
    }
    boolean playersMatch(SteamGame game, int selectedMin, int selectedMax) {
        boolean unrestricted = selectedMin == 1 && selectedMax == 15;
        Integer gameMin = game.getMinPlayers();
        Integer gameMax = game.getMaxPlayers();
        if (gameMin == null || gameMax == null) return unrestricted;
        boolean upperOpen = selectedMax == 15;
        return gameMax >= selectedMin && (upperOpen || gameMin <= selectedMax);
    }
}
