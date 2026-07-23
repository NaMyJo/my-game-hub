package com.mygamehub.lostark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record LostArkProfile(
        String CharacterName,
        String CharacterClassName,
        String CharacterLevel,
        String ItemAvgLevel,
        String CombatPower,
        String ServerName,
        String CharacterImage
) {
}
