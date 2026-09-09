package com.mygamehub.gamefinder;

public interface HardFilterDiagnosticCounts {
    long getEligible();
    long getAfterPlayMode();
    long getAfterPrice();
    long getAfterAdult();
    long getAfterPlayer();
}
