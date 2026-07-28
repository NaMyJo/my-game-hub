package com.mygamehub.pubg;

import java.util.List;

public record PubgPlayerResponse(
        List<PubgPlayerData> data
) {
}