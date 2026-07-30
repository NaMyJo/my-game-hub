package com.mygamehub.overwatch;

public record OverwatchProfile(
        String battleTag,
        String careerUrl,
        String tankRank,
        String damageRank,
        String supportRank
) {
}