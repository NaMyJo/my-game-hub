package com.mygamehub.gamefinder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface SteamGameIgdbTermRepository extends JpaRepository<SteamGameIgdbTerm, Long> {
    @Query("select r from SteamGameIgdbTerm r join fetch r.taxonomyTerm where r.game.steamAppId = :appId")
    List<SteamGameIgdbTerm> findBySteamAppId(@Param("appId") Long appId);

    @Query("select r from SteamGameIgdbTerm r join fetch r.taxonomyTerm where r.game.steamAppId in :appIds")
    List<SteamGameIgdbTerm> findBySteamAppIds(@Param("appIds") Collection<Long> appIds);
}
