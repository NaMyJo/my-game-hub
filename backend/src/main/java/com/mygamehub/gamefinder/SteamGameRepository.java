package com.mygamehub.gamefinder;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.Instant;
import java.util.*;

public interface SteamGameRepository extends JpaRepository<SteamGame, Long> {
    Optional<SteamGame> findBySteamAppId(Long steamAppId);
    long countByGameCatalogEligibleTrue();
    List<SteamGame> findBySteamAppIdIn(Collection<Long> ids);
    @Query(value = "select * from steam_games where game_catalog_eligible=true "
            + "and metadata_status='SUCCESS' and metadata_updated_at is not null "
            + "order by random()", nativeQuery = true)
    List<SteamGame> findMetadataVerificationRandomSample(Pageable pageable);
    @Query(value = "select * from steam_games where game_catalog_eligible=true "
            + "and metadata_status='SUCCESS' and metadata_updated_at is not null "
            + "order by metadata_updated_at desc, steam_app_id", nativeQuery = true)
    List<SteamGame> findMetadataVerificationRecentSample(Pageable pageable);
    @Query("select g from SteamGame g where lower(g.name) like lower(concat('%',:query,'%')) and g.gameCatalogEligible = true and g.storeType = 'game' and g.metadataUpdatedAt is not null and (g.lifecycleStatus is null or g.lifecycleStatus = com.mygamehub.gamefinder.CatalogLifecycleStatus.ACTIVE) order by lower(g.name), g.steamAppId")
    List<SteamGame> findActiveByName(@org.springframework.data.repository.query.Param("query") String query, Pageable pageable);
    @Query("select new com.mygamehub.gamefinder.GameFinderRecommendationCandidate("
            + "g.steamAppId, g.name, g.headerImageUrl, g.priceCurrent, g.priceOriginal, "
            + "g.discountPercent, g.priceCurrency, g.isFree, g.releaseDate, g.releaseDateText, "
            + "g.comingSoon, g.singlePlayer, g.multiplayer, g.onlineCoop, g.maxPlayers, g.genres) "
            + "from SteamGame g where g.steamAppId in :appIds")
    List<GameFinderRecommendationCandidate> findRecommendationCandidatesByAppIds(
            @org.springframework.data.repository.query.Param("appIds") Collection<Long> appIds);
    @Query(value = "select count(*) as eligible, "
            + "count(*) filter (where (:priceUnrestricted=true or "
            + "(case when g.is_free=true then 0 else g.price_current end) between :priceMin and :priceMax)) as \"afterPrice\", "
            + "count(*) filter (where (:priceUnrestricted=true or "
            + "(case when g.is_free=true then 0 else g.price_current end) between :priceMin and :priceMax) "
            + "and (:includeAdult=true or g.adult_status is null or g.adult_status<>'ADULT')) as \"afterAdult\", "
            + "count(*) filter (where (:priceUnrestricted=true or "
            + "(case when g.is_free=true then 0 else g.price_current end) between :priceMin and :priceMax) "
            + "and (:includeAdult=true or g.adult_status is null or g.adult_status<>'ADULT') "
            + "and (:playersUnrestricted=true or "
            + "(greatest(coalesce(g.max_players,0),coalesce(g.online_max_players,0),coalesce(g.online_coop_max_players,0))>=:playerMin "
            + "and coalesce(g.min_players,1)<=:playerMax))) as \"afterPlayer\" "
            + "from steam_games g where g.game_catalog_eligible=true "
            + "and g.metadata_status='SUCCESS' and g.metadata_updated_at is not null "
            + "and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE')",
            nativeQuery = true)
    HardFilterDiagnosticCounts countRecommendationHardFilterStages(
            @org.springframework.data.repository.query.Param("priceMin") int priceMin,
            @org.springframework.data.repository.query.Param("priceMax") int priceMax,
            @org.springframework.data.repository.query.Param("priceUnrestricted") boolean priceUnrestricted,
            @org.springframework.data.repository.query.Param("includeAdult") boolean includeAdult,
            @org.springframework.data.repository.query.Param("playerMin") int playerMin,
            @org.springframework.data.repository.query.Param("playerMax") int playerMax,
            @org.springframework.data.repository.query.Param("playersUnrestricted") boolean playersUnrestricted);
    List<SteamGame> findByMetadataUpdatedAtIsNullOrMetadataUpdatedAtBefore(Instant before, Pageable pageable);
    List<SteamGame> findByPriceUpdatedAtIsNull(Pageable pageable);
    @Query(value="select * from steam_games where ((metadata_status is null and metadata_updated_at is null) "
            + "or metadata_status='PENDING' "
            + "or (metadata_status='RETRYABLE_FAILURE' and (metadata_last_attempt_at is null or metadata_last_attempt_at < :retryBefore)) "
            + "or (metadata_status = 'SUCCESS' and metadata_updated_at < :staleBefore)) "
            + "and game_catalog_eligible=true and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "order by case when metadata_status is null or metadata_status='PENDING' then 0 "
            + "when metadata_status='SUCCESS' then 1 else 2 end, steam_app_id",nativeQuery=true)
    List<SteamGame> findMetadataCandidates(Instant staleBefore, Instant retryBefore, Pageable pageable);
    @Query(value="select * from steam_games where ((metadata_status is null and metadata_updated_at is null) "
            + "or metadata_status='PENDING' "
            + "or (metadata_status='RETRYABLE_FAILURE' and (metadata_last_attempt_at is null or metadata_last_attempt_at < :retryBefore))) "
            + "and game_catalog_eligible=true and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "order by case when metadata_status is null or metadata_status='PENDING' then 0 else 1 end, steam_app_id",nativeQuery=true)
    List<SteamGame> findInitialMetadataCandidates(Instant retryBefore, Pageable pageable);
    @Query(value="select count(*) from steam_games where ((metadata_status is null and metadata_updated_at is null) "
            + "or metadata_status='PENDING' or metadata_status='RETRYABLE_FAILURE') "
            + "and game_catalog_eligible=true and (lifecycle_status is null or lifecycle_status='ACTIVE')",nativeQuery=true)
    long countInitialMetadataIncomplete();
    @Query(value="select count(*) from steam_games where metadata_status='RETRYABLE_FAILURE' "
            + "and metadata_last_attempt_at is not null and metadata_last_attempt_at >= :retryBefore "
            + "and game_catalog_eligible=true and (lifecycle_status is null or lifecycle_status='ACTIVE')",nativeQuery=true)
    long countCoolingMetadataRetryable(Instant retryBefore);
    @Query(value="select min(metadata_last_attempt_at) from steam_games where metadata_status='RETRYABLE_FAILURE' "
            + "and metadata_last_attempt_at is not null and metadata_last_attempt_at >= :retryBefore "
            + "and game_catalog_eligible=true and (lifecycle_status is null or lifecycle_status='ACTIVE')",nativeQuery=true)
    Optional<Instant> findOldestCoolingMetadataAttempt(Instant retryBefore);
    @Query(value="select * from steam_games where metadata_updated_at is not null and store_type='game' and metadata_status='SUCCESS' and "
            + "((igdb_status is null and igdb_updated_at is null) "
            + "or igdb_status in ('PENDING','RETRYABLE_FAILURE') "
            + "or (igdb_status='SUCCESS' and igdb_game_id is not null and "
            + "(igdb_taxonomy_version is null or igdb_taxonomy_version<>'igdb-v2-28'))) and game_catalog_eligible=true "
            + "and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "order by case when igdb_status is null or igdb_status='PENDING' then 0 "
            + "when igdb_status='RETRYABLE_FAILURE' then 1 else 2 end, steam_app_id",nativeQuery=true)
    List<SteamGame> findIgdbCandidates(Pageable pageable);
    @Query(value="select count(*) from steam_games where game_catalog_eligible=true "
            + "and metadata_status='SUCCESS' and metadata_updated_at is not null "
            + "and store_type='game' and ((igdb_status is null and igdb_updated_at is null) "
            + "or igdb_status in ('PENDING','RETRYABLE_FAILURE') "
            + "or (igdb_status='SUCCESS' and igdb_game_id is not null and "
            + "(igdb_taxonomy_version is null or igdb_taxonomy_version<>'igdb-v2-28'))) "
            + "and (lifecycle_status is null or lifecycle_status='ACTIVE')", nativeQuery=true)
    long countIgdbCandidates();
    @Query(value="select count(*) from steam_games where game_catalog_eligible=true "
            + "and (lifecycle_status is null or lifecycle_status='ACTIVE') and ("
            + "((metadata_status is null and metadata_updated_at is null) or metadata_status='PENDING' "
            + "or (metadata_status='RETRYABLE_FAILURE' and (metadata_last_attempt_at is null or metadata_last_attempt_at < :retryBefore)) "
            + "or metadata_status='SUCCESS' and metadata_updated_at < :staleBefore) "
            + "or (metadata_status='SUCCESS' and metadata_updated_at is not null and store_type='game' "
            + "and ((igdb_status is null and igdb_updated_at is null) "
            + "or igdb_status in ('PENDING','RETRYABLE_FAILURE') "
            + "or (igdb_status='SUCCESS' and igdb_game_id is not null and "
            + "(igdb_taxonomy_version is null or igdb_taxonomy_version<>'igdb-v2-28')))))", nativeQuery=true)
    long countEnrichmentCandidates(Instant staleBefore, Instant retryBefore);
    @Query(value="select count(*) from steam_games where ((metadata_status is null and metadata_updated_at is null) "
            + "or metadata_status='PENDING' "
            + "or (metadata_status='RETRYABLE_FAILURE' and (metadata_last_attempt_at is null or metadata_last_attempt_at < :retryBefore)) "
            + "or metadata_status='SUCCESS' and metadata_updated_at < :staleBefore) "
            + "and game_catalog_eligible=true and (lifecycle_status is null or lifecycle_status='ACTIVE')", nativeQuery=true)
    long countMetadataCandidates(Instant staleBefore, Instant retryBefore);
    @Query(value="select g.* from steam_games g where g.game_catalog_eligible=true and g.metadata_updated_at is not null and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') and not exists (select 1 from steam_game_tags t where t.steam_app_id=g.steam_app_id) order by g.steam_app_id",nativeQuery=true)
    List<SteamGame> findTaxonomyCandidates(Pageable pageable);
    @Query(value="select * from steam_games where game_catalog_eligible=true "
            + "and metadata_status='SUCCESS' and metadata_updated_at is not null "
            + "and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "and (coalesce(steam_taxonomy_version,taxonomy_version) is null "
            + "or coalesce(steam_taxonomy_version,taxonomy_version)<>:version) order by steam_app_id", nativeQuery=true)
    List<SteamGame> findTaxonomyVersionCandidates(
            @org.springframework.data.repository.query.Param("version") String version,
            Pageable pageable);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value="update steam_games set lifecycle_status='REMOVED' where (lifecycle_status is null or lifecycle_status='ACTIVE') and (reconciliation_generation is null or reconciliation_generation<>:generation)",nativeQuery=true)
    int markMissingAsRemoved(@org.springframework.data.repository.query.Param("generation") String generation);

    @Query(value = "select count(*) as total, "
            + "coalesce(sum(case when lifecycle_status is null or lifecycle_status='ACTIVE' then 1 else 0 end), 0) as active, "
            + "coalesce(sum(case when lifecycle_status='UNAVAILABLE' then 1 else 0 end), 0) as unavailable, "
            + "coalesce(sum(case when lifecycle_status='REMOVED' then 1 else 0 end), 0) as removed, "
            + "coalesce(sum(case when game_catalog_eligible=true then 1 else 0 end), 0) as \"gameCatalogCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status in ('SUCCESS','NOT_FOUND','PERMANENT_FAILURE') then 1 else 0 end), 0) as \"metadataTerminalCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='NOT_FOUND' then 1 else 0 end), 0) as \"storeUnavailableCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') then 1 else 0 end), 0) as \"igdbTargetCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' and store_type='game' and igdb_status in ('SUCCESS','NOT_FOUND','PERMANENT_FAILURE') then 1 else 0 end), 0) as \"igdbTerminalCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') then 1 else 0 end), 0) as \"finderEligibleCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and (metadata_status='PENDING' or (metadata_status is null and metadata_updated_at is null)) then 1 else 0 end), 0) as \"metadataPending\", "
            + "coalesce(sum(case when game_catalog_eligible=true and (metadata_status='SUCCESS' or (metadata_status is null and metadata_updated_at is not null)) then 1 else 0 end), 0) as \"metadataSuccess\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='NOT_FOUND' then 1 else 0 end), 0) as \"metadataNotFound\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='RETRYABLE_FAILURE' then 1 else 0 end), 0) as \"metadataRetryableFailure\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='PERMANENT_FAILURE' then 1 else 0 end), 0) as \"metadataPermanentFailure\", "
            + "coalesce(sum(case when game_catalog_eligible=true and (igdb_status='PENDING' or (igdb_status is null and igdb_updated_at is null)) then 1 else 0 end), 0) as \"igdbPending\", "
            + "coalesce(sum(case when game_catalog_eligible=true and (igdb_status='SUCCESS' or (igdb_status is null and igdb_updated_at is not null and igdb_game_id is not null)) then 1 else 0 end), 0) as \"igdbSuccess\", "
            + "coalesce(sum(case when game_catalog_eligible=true and (igdb_status='NOT_FOUND' or (igdb_status is null and igdb_updated_at is not null and igdb_game_id is null)) then 1 else 0 end), 0) as \"igdbNotFound\", "
            + "coalesce(sum(case when game_catalog_eligible=true and igdb_status='RETRYABLE_FAILURE' then 1 else 0 end), 0) as \"igdbRetryableFailure\", "
            + "coalesce(sum(case when game_catalog_eligible=true and igdb_status='PERMANENT_FAILURE' then 1 else 0 end), 0) as \"igdbPermanentFailure\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_updated_at is not null and store_type='game' then 1 else 0 end), 0) as \"gameCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_updated_at is not null and store_type is not null and store_type<>'game' then 1 else 0 end), 0) as \"nonGameCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_updated_at is null then 1 else 0 end), 0) as \"unclassifiedCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' and store_type='game' "
            + "and (lifecycle_status is null or lifecycle_status='ACTIVE') and (min_players is not null "
            + "or max_players is not null or online_max_players is not null or online_coop_max_players is not null) "
            + "then 1 else 0 end), 0) as \"playerDataCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' and store_type='game' "
            + "and (lifecycle_status is null or lifecycle_status='ACTIVE') and min_players is null "
            + "and max_players is null and online_max_players is null and online_coop_max_players is null "
            + "then 1 else 0 end), 0) as \"playerDataMissingCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' "
            + "and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "and igdb_status='SUCCESS' then 1 else 0 end), 0) as \"igdbSuccessCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' "
            + "and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "and igdb_status='SUCCESS' and (min_players is not null or max_players is not null "
            + "or online_max_players is not null or online_coop_max_players is not null) "
            + "then 1 else 0 end), 0) as \"igdbSuccessPlayerDataCount\", "
            + "coalesce(sum(case when game_catalog_eligible=true and metadata_status='SUCCESS' "
            + "and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "and igdb_status='SUCCESS' and min_players is null and max_players is null "
            + "and online_max_players is null and online_coop_max_players is null "
            + "then 1 else 0 end), 0) as \"igdbSuccessPlayerDataMissingCount\" "
            + "from steam_games", nativeQuery = true)
    GameFinderAdminStatusProjection adminStatus();

    @Query(value = "select "
            + "count(*) as \"totalChecked\", "
            + "count(*) filter (where igdb_status='SUCCESS' and igdb_game_id is null) as \"successMissingGameId\", "
            + "count(*) filter (where igdb_status='NOT_FOUND' and igdb_game_id is not null) as \"notFoundWithGameId\", "
            + "count(*) filter (where coalesce(min_players,1)<=0 or coalesce(max_players,1)<=0 "
            + "or coalesce(online_max_players,1)<=0 or coalesce(online_coop_max_players,1)<=0 "
            + "or (min_players is not null and greatest(coalesce(max_players,0), "
            + "coalesce(online_max_players,0),coalesce(online_coop_max_players,0))>0 "
            + "and min_players>greatest(coalesce(max_players,0),coalesce(online_max_players,0), "
            + "coalesce(online_coop_max_players,0)))) as \"invalidPlayerRange\", "
            + "(select count(*) from steam_games d where d.igdb_game_id is not null and exists "
            + "(select 1 from steam_games x where x.igdb_game_id=d.igdb_game_id "
            + "and x.steam_app_id<>d.steam_app_id)) as \"duplicateIgdbMapping\", "
            + "(select coalesce(sum(c-1),0) from (select count(*) c from steam_game_igdb_terms "
            + "group by steam_app_id,taxonomy_term_id having count(*)>1) duplicates) "
            + "as \"duplicateTaxonomyRelation\" "
            + "from steam_games where game_catalog_eligible=true and metadata_status='SUCCESS' "
            + "and store_type='game' and (lifecycle_status is null or lifecycle_status='ACTIVE') "
            + "and igdb_status in ('SUCCESS','NOT_FOUND','RETRYABLE_FAILURE','PERMANENT_FAILURE')",
            nativeQuery = true)
    IgdbIntegrityProjection verifyIgdbIntegrity();
}
