package com.mygamehub.gamefinder;

public enum MaintenanceJobStatus {
    IDLE, RUNNING, WAITING_RATE_LIMIT, WAITING_RETRY,
    STOP_REQUESTED, STOPPED, COMPLETED, FAILED;

    public boolean active() {
        return this == RUNNING || this == WAITING_RATE_LIMIT || this == WAITING_RETRY
                || this == STOP_REQUESTED;
    }
}
