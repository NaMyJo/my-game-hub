package com.mygamehub.gamefinder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.*;

public interface SteamGameRepository extends JpaRepository<SteamGame, Long> {
    Optional<SteamGame> findBySteamAppId(Long steamAppId);
    List<SteamGame> findBySteamAppIdIn(Collection<Long> ids);
    List<SteamGame> findByNameContainingIgnoreCaseAndMetadataUpdatedAtIsNotNull(String query, Pageable pageable);
    @Query("select g from SteamGame g where g.metadataUpdatedAt is not null and g.storeType = 'game'")
    List<SteamGame> findRecommendationCandidates();
    List<SteamGame> findByMetadataUpdatedAtIsNullOrMetadataUpdatedAtBefore(Instant before, Pageable pageable);
    List<SteamGame> findByPriceUpdatedAtIsNull(Pageable pageable);
}
