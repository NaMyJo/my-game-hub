package com.mygamehub.gamefinder;

public interface GameFinderAdminStatusProjection {
    long getTotal();
    long getActive();
    long getUnavailable();
    long getRemoved();
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
}
