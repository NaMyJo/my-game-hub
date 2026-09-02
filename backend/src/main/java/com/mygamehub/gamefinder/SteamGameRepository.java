package com.mygamehub.gamefinder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.*;

public interface SteamGameRepository extends JpaRepository<SteamGame, Long> {
    Optional<SteamGame> findBySteamAppId(Long steamAppId);
    List<SteamGame> findBySteamAppIdIn(Collection<Long> ids);
    @Query("select g from SteamGame g where lower(g.name) like lower(concat('%',:query,'%')) and g.metadataUpdatedAt is not null and (g.lifecycleStatus is null or g.lifecycleStatus = com.mygamehub.gamefinder.CatalogLifecycleStatus.ACTIVE) order by lower(g.name), g.steamAppId")
    List<SteamGame> findActiveByName(@org.springframework.data.repository.query.Param("query") String query, Pageable pageable);
    @Query("select g from SteamGame g where g.metadataUpdatedAt is not null and g.storeType = 'game' and (g.lifecycleStatus is null or g.lifecycleStatus = com.mygamehub.gamefinder.CatalogLifecycleStatus.ACTIVE)")
    List<SteamGame> findRecommendationCandidates();
    List<SteamGame> findByMetadataUpdatedAtIsNullOrMetadataUpdatedAtBefore(Instant before, Pageable pageable);
    List<SteamGame> findByPriceUpdatedAtIsNull(Pageable pageable);
    @Query(value="select * from steam_games where (metadata_status is null "
            + "or metadata_status in ('PENDING','RETRYABLE_FAILURE') "
            + "or ((metadata_status = 'SUCCESS' or metadata_status is null) and metadata_updated_at < :staleBefore)) "
            + "and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "order by steam_app_id",nativeQuery=true)
    List<SteamGame> findMetadataCandidates(Instant staleBefore, Pageable pageable);
    @Query(value="select * from steam_games where metadata_updated_at is not null and store_type = 'game' and "
            + "(igdb_status is null "
            + "or igdb_status in ('PENDING','RETRYABLE_FAILURE')) and (lifecycle_status is null or lifecycle_status='ACTIVE') order by steam_app_id",nativeQuery=true)
    List<SteamGame> findIgdbCandidates(Pageable pageable);
    @Query(value="select g.* from steam_games g where g.metadata_updated_at is not null and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') and not exists (select 1 from steam_game_tags t where t.steam_app_id=g.steam_app_id) order by g.steam_app_id",nativeQuery=true)
    List<SteamGame> findTaxonomyCandidates(Pageable pageable);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value="update steam_games set lifecycle_status='REMOVED' where (lifecycle_status is null or lifecycle_status='ACTIVE') and (reconciliation_generation is null or reconciliation_generation<>:generation)",nativeQuery=true)
    int markMissingAsRemoved(@org.springframework.data.repository.query.Param("generation") String generation);

    @Query(value = "select count(*) as total, "
            + "coalesce(sum(case when lifecycle_status is null or lifecycle_status='ACTIVE' then 1 else 0 end), 0) as active, "
            + "coalesce(sum(case when lifecycle_status='UNAVAILABLE' then 1 else 0 end), 0) as unavailable, "
            + "coalesce(sum(case when lifecycle_status='REMOVED' then 1 else 0 end), 0) as removed, "
            + "coalesce(sum(case when metadata_status='PENDING' or (metadata_status is null and metadata_updated_at is null) then 1 else 0 end), 0) as \"metadataPending\", "
            + "coalesce(sum(case when metadata_status='SUCCESS' or (metadata_status is null and metadata_updated_at is not null) then 1 else 0 end), 0) as \"metadataSuccess\", "
            + "coalesce(sum(case when metadata_status='NOT_FOUND' then 1 else 0 end), 0) as \"metadataNotFound\", "
            + "coalesce(sum(case when metadata_status='RETRYABLE_FAILURE' then 1 else 0 end), 0) as \"metadataRetryableFailure\", "
            + "coalesce(sum(case when metadata_status='PERMANENT_FAILURE' then 1 else 0 end), 0) as \"metadataPermanentFailure\", "
            + "coalesce(sum(case when igdb_status='PENDING' or (igdb_status is null and igdb_updated_at is null) then 1 else 0 end), 0) as \"igdbPending\", "
            + "coalesce(sum(case when igdb_status='SUCCESS' or (igdb_status is null and igdb_updated_at is not null and igdb_game_id is not null) then 1 else 0 end), 0) as \"igdbSuccess\", "
            + "coalesce(sum(case when igdb_status='NOT_FOUND' or (igdb_status is null and igdb_updated_at is not null and igdb_game_id is null) then 1 else 0 end), 0) as \"igdbNotFound\", "
            + "coalesce(sum(case when igdb_status='RETRYABLE_FAILURE' then 1 else 0 end), 0) as \"igdbRetryableFailure\", "
            + "coalesce(sum(case when igdb_status='PERMANENT_FAILURE' then 1 else 0 end), 0) as \"igdbPermanentFailure\" "
            + "from steam_games", nativeQuery = true)
    GameFinderAdminStatusProjection adminStatus();
}
