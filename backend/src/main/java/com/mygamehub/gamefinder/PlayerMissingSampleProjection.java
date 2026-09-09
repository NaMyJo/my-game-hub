package com.mygamehub.gamefinder;

public interface PlayerMissingSampleProjection {
    Long getSteamAppId();
    String getName();
    Long getIgdbGameId();
    String getCanonicalTags();
    String getIgdbGameModes();
    Integer getMinPlayers();
    Integer getMaxPlayers();
    Integer getOnlineMaxPlayers();
    Integer getOnlineCoopMaxPlayers();
}
