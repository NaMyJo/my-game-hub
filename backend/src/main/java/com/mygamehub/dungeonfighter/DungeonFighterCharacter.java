package com.mygamehub.dungeonfighter;

public record DungeonFighterCharacter(
        String serverId,
        String characterId,
        String characterName,
        Integer level,
        String jobId,
        String jobGrowId,
        String jobName,
        String jobGrowName,
        Integer fame
) {
}