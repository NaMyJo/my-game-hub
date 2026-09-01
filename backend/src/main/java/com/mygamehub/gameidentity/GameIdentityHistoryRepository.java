package com.mygamehub.gameidentity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameIdentityHistoryRepository
        extends JpaRepository<GameIdentityHistory, Long> {

    Optional<GameIdentityHistory> findByUserUid(
            String userUid
    );

    Optional<GameIdentityHistory> findByShareIdAndShareEnabledTrue(
            String shareId
    );
}
