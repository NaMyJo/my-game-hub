package com.mygamehub.gamefinder;

public interface GameFinderAdminStatusProjection {
    long getTotal();
    long getActive();
    long getUnavailable();
    long getRemoved();
    long getGameCatalogCount();
    long getMetadataTerminalCount();
    long getStoreUnavailableCount();
    long getIgdbTargetCount();
    long getIgdbTerminalCount();
    long getFinderEligibleCount();
    long getMetadataPending();
    long getMetadataSuccess();
    long getMetadataNotFound();
    long getMetadataRetryableFailure();
    long getMetadataPermanentFailure();
    long getIgdbPending();
    long getIgdbSuccess();
    long getIgdbNotFound();
    long getIgdbRetryableFailure();
    long getIgdbPermanentFailure();
    long getGameCount();
    long getNonGameCount();
    long getUnclassifiedCount();
}
