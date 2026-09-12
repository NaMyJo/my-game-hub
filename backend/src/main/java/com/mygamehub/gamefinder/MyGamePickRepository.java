package com.mygamehub.gamefinder;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface MyGamePickRepository extends JpaRepository<MyGamePick, Long> {
    List<MyGamePick> findAllByFirebaseUidOrderByCreatedAtDesc(String firebaseUid);
    boolean existsByFirebaseUidAndSteamAppId(String firebaseUid, Long steamAppId);
    Optional<MyGamePick> findByFirebaseUidAndSteamAppId(String firebaseUid, Long steamAppId);
    void deleteByFirebaseUidAndSteamAppId(String firebaseUid, Long steamAppId);
    void deleteAllByFirebaseUid(String firebaseUid);
}
