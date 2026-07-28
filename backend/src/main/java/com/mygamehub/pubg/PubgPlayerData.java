package com.mygamehub.pubg;

public record PubgPlayerData(
        String type,
        String id,
        PubgPlayerAttributes attributes
) {
}