package com.mygamehub.gamefinder.dto;

import com.mygamehub.gamefinder.PlayerMissingSampleProjection;
import java.util.Arrays;
import java.util.List;

public record PlayerMissingSampleResponse(
        String classification,
        int limit,
        List<Game> games
) {
    public record Game(
            long steamAppId,
            String name,
            Long igdbGameId,
            List<String> canonicalTags,
            List<String> igdbGameModes,
            Integer minPlayers,
            Integer maxPlayers,
            Integer onlineMaxPlayers,
            Integer onlineCoopMaxPlayers
    ) {
        public static Game from(PlayerMissingSampleProjection value) {
            return new Game(value.getSteamAppId(), value.getName(), value.getIgdbGameId(),
                    split(value.getCanonicalTags()), split(value.getIgdbGameModes()),
                    value.getMinPlayers(), value.getMaxPlayers(), value.getOnlineMaxPlayers(),
                    value.getOnlineCoopMaxPlayers());
        }

        private static List<String> split(String value) {
            if (value == null || value.isBlank()) return List.of();
            return Arrays.stream(value.split("\\|"))
                    .filter(item -> !item.isBlank()).toList();
        }
    }
}
