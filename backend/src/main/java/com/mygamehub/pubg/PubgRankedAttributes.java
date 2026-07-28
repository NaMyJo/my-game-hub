package com.mygamehub.pubg;

import java.util.Map;

public record PubgRankedAttributes(
        Map<String, PubgRankedStats> rankedGameModeStats
) {
}