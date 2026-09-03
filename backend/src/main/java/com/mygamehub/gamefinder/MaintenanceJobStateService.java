package com.mygamehub.gamefinder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service
public class MaintenanceJobStateService {
    private final GameFinderMaintenanceJobRepository jobs;

    public MaintenanceJobStateService(GameFinderMaintenanceJobRepository jobs) { this.jobs = jobs; }

    @Transactional
    public GameFinderMaintenanceJob getOrCreate(MaintenanceJobType type) {
        return jobs.findById(type).orElseGet(() -> jobs.save(new GameFinderMaintenanceJob(type)));
    }
    @Transactional
    public GameFinderMaintenanceJob start(MaintenanceJobType type, Instant now) {
        var job = getOrCreate(type); job.start(now); return jobs.save(job);
    }
    @Transactional
    public GameFinderMaintenanceJob save(GameFinderMaintenanceJob job) { return jobs.save(job); }
}
