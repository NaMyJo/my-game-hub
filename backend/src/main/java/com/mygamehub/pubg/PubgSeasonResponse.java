package com.mygamehub.pubg;

import java.util.List;

public record PubgSeasonResponse(
        List<PubgSeasonData> data
) {
}