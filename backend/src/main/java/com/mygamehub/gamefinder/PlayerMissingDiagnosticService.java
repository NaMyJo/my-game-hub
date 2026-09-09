package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.PlayerMissingSampleResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerMissingDiagnosticService {
    public enum Classification {
        MULTIPLAYER_CANDIDATE, SINGLEPLAYER_ONLY_CANDIDATE, UNKNOWN
    }

    private final SteamGameRepository games;

    public PlayerMissingDiagnosticService(SteamGameRepository games) {
        this.games = games;
    }

    @Transactional(readOnly = true)
    public PlayerMissingSampleResponse samples(Classification classification, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        var values = games.findPlayerMissingSamples(
                classification.name(), PageRequest.of(0, boundedLimit));
        return new PlayerMissingSampleResponse(classification.name(), boundedLimit,
                values.stream().map(PlayerMissingSampleResponse.Game::from).toList());
    }
}
