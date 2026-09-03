package com.mygamehub.gamefinder;
import com.mygamehub.gamefinder.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
@Service
public class GameFinderPreferenceService {
 private final GameFinderUserPreferenceRepository preferences; private final GameFinderRecentSeedRepository recents;
 private final SteamGameRepository games; private final GameTagTaxonomy taxonomy;
 public GameFinderPreferenceService(GameFinderUserPreferenceRepository p,GameFinderRecentSeedRepository r,SteamGameRepository g,GameTagTaxonomy t){preferences=p;recents=r;games=g;taxonomy=t;}
 @Transactional(readOnly=true) public GameFinderPreferenceResponse get(String uid){
  var p=preferences.findById(uid).orElse(null); var selected=p==null?List.<Long>of():p.selectedIds();
  return response(p,selected,recents.findByFirebaseUidOrderBySelectedAtDesc(uid,PageRequest.of(0,20)).stream().map(GameFinderRecentSeed::getSteamAppId).toList());
 }
 @Transactional public GameFinderPreferenceResponse save(String uid,GameFinderPreferenceRequest body){
  if(body.priceMin()>body.priceMax()||body.playerMin()>body.playerMax())throw new IllegalArgumentException("최소 범위는 최대 범위보다 클 수 없습니다.");
  var found=games.findBySteamAppIdIn(body.selectedSteamAppIds()); if(found.size()!=new HashSet<>(body.selectedSteamAppIds()).size())throw new IllegalArgumentException("선택한 Steam 게임 정보를 찾을 수 없습니다.");
  var tags=new LinkedHashSet<String>(); for(var v:body.preferredTags())tags.add(taxonomy.normalize(v).orElseThrow(()->new IllegalArgumentException("지원하지 않는 선호 태그입니다: "+v)));
  var p=preferences.findById(uid).orElseGet(()->new GameFinderUserPreference(uid)); p.update(body.selectedSteamAppIds(),tags,body.priceMin(),body.priceMax(),body.includeAdult(),body.playerMin(),body.playerMax()); preferences.save(p);
  Instant now=Instant.now(); int order=0; for(long id:body.selectedSteamAppIds()){Instant selectedAt=now.plusNanos(order++);var recent=recents.findByFirebaseUidAndSteamAppId(uid,id).orElseGet(()->new GameFinderRecentSeed(uid,id,selectedAt));recent.touch(selectedAt);recents.save(recent);}
  var retained=recents.findByFirebaseUidOrderBySelectedAtDesc(uid,PageRequest.of(0,30));
  if(retained.size()>20)recents.deleteAll(retained.subList(20,retained.size()));
  return response(p,body.selectedSteamAppIds(),recents.findByFirebaseUidOrderBySelectedAtDesc(uid,PageRequest.of(0,20)).stream().map(GameFinderRecentSeed::getSteamAppId).toList());
 }
 private GameFinderPreferenceResponse response(GameFinderUserPreference p,List<Long> selected,List<Long> recent){
  Set<Long> allIds=new LinkedHashSet<>(selected); allIds.addAll(recent);
  Map<Long,SteamGame> byId=new HashMap<>(); games.findBySteamAppIdIn(new ArrayList<>(allIds)).forEach(g->byId.put(g.getSteamAppId(),g));
  return new GameFinderPreferenceResponse(items(selected,byId),p==null?List.of():p.tags(),p==null?0:p.getPriceMin(),p==null?100000:p.getPriceMax(),p!=null&&p.isIncludeAdult(),p==null?1:p.getPlayerMin(),p==null?15:p.getPlayerMax(),items(recent,byId));
 }
 private List<GameFinderSearchResponse> items(List<Long> ids,Map<Long,SteamGame> byId){return ids.stream().map(byId::get).filter(Objects::nonNull).map(g->new GameFinderSearchResponse(g.getSteamAppId(),g.getName(),g.getHeaderImageUrl())).toList();}
}
