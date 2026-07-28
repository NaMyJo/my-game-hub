package com.mygamehub.pubg;

public record PubgSeasonData(
        String type,
        String id,
        PubgSeasonAttributes attributes
) {
}