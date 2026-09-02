package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderAdminStatusResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GameFinderAdminStatusService {
    private final SteamGameRepository games;

    public GameFinderAdminStatusService(SteamGameRepository games) {
        this.games = games;
    }

    @Transactional(readOnly = true)
    public GameFinderAdminStatusResponse status() {
        return GameFinderAdminStatusResponse.from(games.adminStatus());
    }
}
