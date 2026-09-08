package com.mygamehub.gamefinder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface SteamGameTagRepository extends JpaRepository<SteamGameTag,Long>{
    List<SteamGameTag> findBySteamAppId(Long appId);
    void deleteBySteamAppId(Long appId);
    @Query(value="select s.steam_app_id from steam_game_tags s join game_tags t on t.id=s.tag_id join steam_games g on g.steam_app_id=s.steam_app_id where t.canonical_name in (:tags) and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') group by s.steam_app_id having count(distinct t.canonical_name)=:tagCount order by s.steam_app_id",nativeQuery=true)
    List<Long> findAppIdsMatchingAll(@Param("tags") Collection<String> tags,@Param("tagCount") long tagCount,Pageable pageable);
    @Query("select t.tag.canonicalName from SteamGameTag t where t.steamAppId=:steamAppId order by t.tag.canonicalName")
    List<String> findCanonicalNamesBySteamAppId(@Param("steamAppId") Long steamAppId);
    @Query("select t.steamAppId as steamAppId, t.tag.canonicalName as canonicalName "
            + "from SteamGameTag t where t.steamAppId in :appIds")
    List<SteamGameTagValue> findCanonicalNamesBySteamAppIds(
            @Param("appIds") Collection<Long> appIds);

    @Query(value="select s.steam_app_id from steam_game_tags s join game_tags t on t.id=s.tag_id "
            + "join steam_games g on g.steam_app_id=s.steam_app_id "
            + "where t.canonical_name in (:tags) and g.game_catalog_eligible=true "
            + "and g.metadata_status='SUCCESS' and g.metadata_updated_at is not null "
            + "and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') "
            + "and (:priceUnrestricted=true or (case when g.is_free=true then 0 else g.price_current end) "
            + "between :priceMin and :priceMax) and (:includeAdult=true or g.adult_status is null or g.adult_status<>'ADULT') "
            + "and (:playersUnrestricted=true or (g.min_players is not null and g.max_players is not null "
            + "and g.max_players>=:playerMin and (:playerUpperOpen=true or g.min_players<=:playerMax))) "
            + "group by s.steam_app_id having count(distinct t.canonical_name)=:tagCount "
            + "order by s.steam_app_id",nativeQuery=true)
    List<Long> findFilteredAppIdsMatchingAll(@Param("tags") Collection<String> tags,
            @Param("tagCount") long tagCount, @Param("priceMin") int priceMin,
            @Param("priceMax") int priceMax, @Param("priceUnrestricted") boolean priceUnrestricted,
            @Param("includeAdult") boolean includeAdult, @Param("playerMin") int playerMin,
            @Param("playerMax") int playerMax, @Param("playersUnrestricted") boolean playersUnrestricted,
            @Param("playerUpperOpen") boolean playerUpperOpen, Pageable pageable);
    @Query(value="select s.steam_app_id from steam_game_tags s join game_tags t on t.id=s.tag_id "
            + "join steam_games g on g.steam_app_id=s.steam_app_id "
            + "where t.canonical_name in (:tags) and g.game_catalog_eligible=true "
            + "and g.metadata_status='SUCCESS' and g.metadata_updated_at is not null "
            + "and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') "
            + "and (:priceUnrestricted=true or (case when g.is_free=true then 0 else g.price_current end) between :priceMin and :priceMax) "
            + "and (:includeAdult=true or g.adult_status is null or g.adult_status<>'ADULT') "
            + "and (:playersUnrestricted=true or (g.min_players is not null and g.max_players is not null "
            + "and g.max_players>=:playerMin and (:playerUpperOpen=true or g.min_players<=:playerMax))) "
            + "group by s.steam_app_id order by count(distinct t.canonical_name) desc, s.steam_app_id",nativeQuery=true)
    List<Long> findRelevantRecommendationAppIds(@Param("tags") Collection<String> tags,
            @Param("priceMin") int priceMin, @Param("priceMax") int priceMax,
            @Param("priceUnrestricted") boolean priceUnrestricted,
            @Param("includeAdult") boolean includeAdult, @Param("playerMin") int playerMin,
            @Param("playerMax") int playerMax, @Param("playersUnrestricted") boolean playersUnrestricted,
            @Param("playerUpperOpen") boolean playerUpperOpen, Pageable pageable);
}
