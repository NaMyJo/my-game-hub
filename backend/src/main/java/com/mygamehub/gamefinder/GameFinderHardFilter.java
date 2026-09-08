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
        Integer gameMax = maximumKnownPlayers(game);
        if (gameMax == null) return unrestricted;
        // IGDB exposes maxima per multiplayer mode. The enrichment model represents a
        // known positive maximum as the continuous supported interval starting at one.
        if (gameMin == null) gameMin = 1;
        return gameMax >= selectedMin && gameMin <= selectedMax;
    }

    private Integer maximumKnownPlayers(SteamGame game) {
        Integer result = null;
        for (Integer value : new Integer[] {game.getMaxPlayers(), game.getOnlineMaxPlayers(),
                game.getOnlineCoopMaxPlayers()}) {
            if (value != null && value > 0) result = result == null ? value : Math.max(result, value);
        }
        return result;
    }
}
