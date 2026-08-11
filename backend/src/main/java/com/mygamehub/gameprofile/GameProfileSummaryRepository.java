package com.mygamehub.gameprofile;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameProfileSummaryRepository
        extends JpaRepository<GameProfileSummary, Long> {

    Optional<GameProfileSummary>
    findByUserUid(String userUid);
}