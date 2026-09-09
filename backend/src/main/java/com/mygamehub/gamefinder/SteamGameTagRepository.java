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
    @Query("select t from SteamGameTag t join fetch t.tag where t.steamAppId in :appIds")
    List<SteamGameTag> findBySteamAppIds(@Param("appIds") Collection<Long> appIds);

    @Query(value="select s.steam_app_id from steam_game_tags s join game_tags t on t.id=s.tag_id "
            + "join steam_games g on g.steam_app_id=s.steam_app_id "
            + "where t.canonical_name in (:tags) and g.game_catalog_eligible=true "
            + "and g.metadata_status='SUCCESS' and g.metadata_updated_at is not null "
            + "and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') "
            + "and (:playMode is null or (:playMode='SINGLE' and exists (select 1 from steam_game_tags ps join game_tags pt on pt.id=ps.tag_id where ps.steam_app_id=g.steam_app_id and pt.canonical_name='singleplayer')) "
            + "or (:playMode='MULTI' and (exists (select 1 from steam_game_tags pm join game_tags mt on mt.id=pm.tag_id where pm.steam_app_id=g.steam_app_id and mt.canonical_name in ('multiplayer','coop','online-coop','local-coop','pvp','online-pvp','massively-multiplayer')) or exists (select 1 from steam_game_igdb_terms im join igdb_taxonomy_terms it on it.id=im.taxonomy_term_id where im.steam_app_id=g.steam_app_id and it.source_type='GAME_MODE' and it.igdb_term_id in (2,3,4,5,6))))) "
            + "and ((:priceMode is null and (:priceUnrestricted=true or ((case when g.is_free=true then 0 else g.price_current end)>=:priceMin and (:priceMax=100000 or (case when g.is_free=true then 0 else g.price_current end)<=:priceMax)))) "
            + "or (:priceMode='FREE' and g.is_free=true) or (:priceMode='PAID' and g.is_free is not true and g.price_current is not null and g.price_current>=:priceMin and (:priceMax=100000 or g.price_current<=:priceMax))) "
            + "and (:includeAdult=true or g.adult_status is null or g.adult_status<>'ADULT') "
            + "and (:playMode='SINGLE' or :playersUnrestricted=true or ("
            + "greatest(coalesce(g.max_players,0),coalesce(g.online_max_players,0),coalesce(g.online_coop_max_players,0))>=:playerMin "
            + "and (:playerMax=15 or coalesce(g.min_players,1)<=:playerMax))) "
            + "group by s.steam_app_id having count(distinct t.canonical_name)=:tagCount "
            + "order by s.steam_app_id",nativeQuery=true)
    List<Long> findFilteredAppIdsMatchingAll(@Param("tags") Collection<String> tags,
            @Param("tagCount") long tagCount, @Param("priceMin") int priceMin,
            @Param("priceMax") int priceMax, @Param("priceUnrestricted") boolean priceUnrestricted,
            @Param("includeAdult") boolean includeAdult, @Param("playerMin") int playerMin,
            @Param("playerMax") int playerMax, @Param("playersUnrestricted") boolean playersUnrestricted,
            @Param("playMode") String playMode, @Param("priceMode") String priceMode,
            Pageable pageable);
    default List<Long> findFilteredAppIdsMatchingAll(Collection<String> tags, long tagCount,
            int priceMin, int priceMax, boolean priceUnrestricted, boolean includeAdult,
            int playerMin, int playerMax, boolean playersUnrestricted, Pageable pageable) {
        return findFilteredAppIdsMatchingAll(tags, tagCount, priceMin, priceMax,
                priceUnrestricted, includeAdult, playerMin, playerMax, playersUnrestricted,
                null, null, pageable);
    }
    @Query(value="select g.steam_app_id from steam_games g "
            + "left join steam_game_tags s on s.steam_app_id=g.steam_app_id and s.tag_id in "
            + "(select t.id from game_tags t where t.canonical_name in (:tags)) "
            + "where g.game_catalog_eligible=true "
            + "and g.metadata_status='SUCCESS' and g.metadata_updated_at is not null "
            + "and g.store_type='game' and (g.lifecycle_status is null or g.lifecycle_status='ACTIVE') "
            + "and (:playMode is null or (:playMode='SINGLE' and exists (select 1 from steam_game_tags ps join game_tags pt on pt.id=ps.tag_id where ps.steam_app_id=g.steam_app_id and pt.canonical_name='singleplayer')) "
            + "or (:playMode='MULTI' and (exists (select 1 from steam_game_tags pm join game_tags mt on mt.id=pm.tag_id where pm.steam_app_id=g.steam_app_id and mt.canonical_name in ('multiplayer','coop','online-coop','local-coop','pvp','online-pvp','massively-multiplayer')) or exists (select 1 from steam_game_igdb_terms im join igdb_taxonomy_terms it on it.id=im.taxonomy_term_id where im.steam_app_id=g.steam_app_id and it.source_type='GAME_MODE' and it.igdb_term_id in (2,3,4,5,6))))) "
            + "and ((:priceMode is null and (:priceUnrestricted=true or ((case when g.is_free=true then 0 else g.price_current end)>=:priceMin and (:priceMax=100000 or (case when g.is_free=true then 0 else g.price_current end)<=:priceMax)))) "
            + "or (:priceMode='FREE' and g.is_free=true) or (:priceMode='PAID' and g.is_free is not true and g.price_current is not null and g.price_current>=:priceMin and (:priceMax=100000 or g.price_current<=:priceMax))) "
            + "and (:includeAdult=true or g.adult_status is null or g.adult_status<>'ADULT') "
            + "and (:playMode='SINGLE' or :playersUnrestricted=true or ("
            + "greatest(coalesce(g.max_players,0),coalesce(g.online_max_players,0),coalesce(g.online_coop_max_players,0))>=:playerMin "
            + "and (:playerMax=15 or coalesce(g.min_players,1)<=:playerMax))) "
            + "group by g.steam_app_id,g.release_date "
            + "order by count(distinct s.tag_id) desc, "
            + "case when :preferRecent=true and g.release_date is not null "
            + "and g.release_date<=current_date then g.release_date end desc nulls last, "
            + "mod(g.steam_app_id*1103515245+:tieSeed,2147483647),g.steam_app_id",nativeQuery=true)
    List<Long> findRankedRecommendationAppIds(@Param("tags") Collection<String> tags,
            @Param("priceMin") int priceMin, @Param("priceMax") int priceMax,
            @Param("priceUnrestricted") boolean priceUnrestricted,
            @Param("includeAdult") boolean includeAdult, @Param("playerMin") int playerMin,
            @Param("playerMax") int playerMax, @Param("playersUnrestricted") boolean playersUnrestricted,
            @Param("playMode") String playMode, @Param("priceMode") String priceMode,
            @Param("preferRecent") boolean preferRecent, @Param("tieSeed") long tieSeed,
            Pageable pageable);
    default List<Long> findRankedRecommendationAppIds(Collection<String> tags,
            int priceMin, int priceMax, boolean priceUnrestricted, boolean includeAdult,
            int playerMin, int playerMax, boolean playersUnrestricted,
            boolean preferRecent, long tieSeed, Pageable pageable) {
        return findRankedRecommendationAppIds(tags, priceMin, priceMax, priceUnrestricted,
                includeAdult, playerMin, playerMax, playersUnrestricted, null, null,
                preferRecent, tieSeed, pageable);
    }
}
