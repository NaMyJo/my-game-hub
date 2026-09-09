package com.mygamehub.gamefinder;

public interface PlayerMissingClassificationProjection {
    long getPlayerDataMissingTotal();
    long getMultiplayerCandidateCount();
    long getSingleplayerOnlyCandidateCount();
    long getUnknownCount();
    long getRecoverablePlayerCount();
    long getMultiplayerKnownButCapacityUnknown();
    long getInsufficientSourceCount();
    long getIgdbSuccessPlayerDataMissingTotal();
    long getIgdbSuccessMultiplayerCandidateCount();
    long getIgdbSuccessSingleplayerOnlyCandidateCount();
    long getIgdbSuccessUnknownCount();
}
