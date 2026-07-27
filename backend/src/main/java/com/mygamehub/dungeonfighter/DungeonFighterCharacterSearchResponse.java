package com.mygamehub.dungeonfighter;

import java.util.List;

public record DungeonFighterCharacterSearchResponse(
        List<DungeonFighterCharacter> rows
) {
}