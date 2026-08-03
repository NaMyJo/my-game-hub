package com.mygamehub.gameidentity;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GameIdentityEntryRepository
        extends JpaRepository<GameIdentityEntry, Long> {
}