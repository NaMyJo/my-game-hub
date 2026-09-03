package com.mygamehub.gamefinder;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameFinderMaintenanceJobRepository
        extends JpaRepository<GameFinderMaintenanceJob, MaintenanceJobType> {}
