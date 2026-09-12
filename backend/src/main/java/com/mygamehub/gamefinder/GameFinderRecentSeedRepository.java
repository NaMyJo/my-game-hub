package com.mygamehub.gamefinder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface GameFinderRecentSeedRepository extends JpaRepository<GameFinderRecentSeed,Long>{
 Optional<GameFinderRecentSeed> findByFirebaseUidAndSteamAppId(String uid,long appId);
 void deleteByFirebaseUidAndSteamAppId(String uid,long appId);
 void deleteAllByFirebaseUid(String uid);
 List<GameFinderRecentSeed> findByFirebaseUidOrderBySelectedAtDesc(String uid,Pageable pageable);
}
