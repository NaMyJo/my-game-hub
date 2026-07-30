package com.mygamehub.valorant;

public record ValorantProfile(
        String name,
        String tag,
        String region,
        String tier,
        Integer rr,
        Integer accountLevel,
        String cardImageUrl
) {
}