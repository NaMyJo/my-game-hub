package com.mygamehub.game;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameAccountRepository extends JpaRepository<GameAccount, Long> {

    List<GameAccount> findAllByFirebaseUidOrderByDisplayOrderAscIdAsc(
            String firebaseUid
    );

    Optional<GameAccount> findByIdAndFirebaseUid(
            Long id,
            String firebaseUid
    );

    long countByFirebaseUid(
            String firebaseUid
    );

    boolean existsByFirebaseUidAndGameTypeAndAccountName(
            String firebaseUid,
            GameType gameType,
            String accountName
    );
}