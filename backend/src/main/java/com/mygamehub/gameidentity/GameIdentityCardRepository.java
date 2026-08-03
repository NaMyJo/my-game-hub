package com.mygamehub.gameidentity;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameIdentityCardRepository
        extends JpaRepository<GameIdentityCard, Long> {

    List<GameIdentityCard>
    findAllByFirebaseUidOrderByCreatedAtDesc(
            String firebaseUid
    );

    Optional<GameIdentityCard>
    findByIdAndFirebaseUid(
            Long id,
            String firebaseUid
    );
}