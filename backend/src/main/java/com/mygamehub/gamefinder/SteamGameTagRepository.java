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
}
