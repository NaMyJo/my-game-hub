package com.mygamehub.gamefinder;

public interface PlayerMissingClassificationProjection {
    long getPlayerDataMissingTotal();
    long getMultiplayerCandidateCount();
    long getSingleplayerOnlyCandidateCount();
    long getUnknownCount();
    long getIgdbSuccessPlayerDataMissingTotal();
    long getIgdbSuccessMultiplayerCandidateCount();
    long getIgdbSuccessSingleplayerOnlyCandidateCount();
    long getIgdbSuccessUnknownCount();
}
