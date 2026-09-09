package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.PlayerMissingSampleResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlayerMissingDiagnosticService {
    public enum Classification {
        MULTIPLAYER_CANDIDATE, SINGLEPLAYER_ONLY_CANDIDATE, UNKNOWN,
        RECOVERABLE, CAPACITY_UNKNOWN, INSUFFICIENT
    }

    private final SteamGameRepository games;

    public PlayerMissingDiagnosticService(SteamGameRepository games) {
        this.games = games;
    }

    @Transactional(readOnly = true)
    public PlayerMissingSampleResponse samples(Classification classification, int limit) {
        int boundedLimit = Math.max(1, Math.min(limit, 50));
        String storedClassification = classification == Classification.CAPACITY_UNKNOWN
                ? Classification.MULTIPLAYER_CANDIDATE.name() : classification.name();
        var values = classification == Classification.RECOVERABLE
                        || classification == Classification.INSUFFICIENT
                ? java.util.List.<PlayerMissingSampleProjection>of()
                : games.findPlayerMissingSamples(storedClassification,
                        PageRequest.of(0, boundedLimit));
        return new PlayerMissingSampleResponse(classification.name(), boundedLimit,
                values.stream().map(value -> PlayerMissingSampleResponse.Game.from(
                        value, storedClassification)).toList());
    }
}
