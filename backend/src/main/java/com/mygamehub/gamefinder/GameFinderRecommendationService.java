package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GameFinderRecommendationService {
    private final SteamGameRepository repository;
    private final GameFinderHardFilter hardFilter;
    public GameFinderRecommendationService(SteamGameRepository repository, GameFinderHardFilter hardFilter) {
        this.repository=repository; this.hardFilter=hardFilter;
    }
    public List<GameFinderSearchResponse> search(String query) {
        if (query == null || query.trim().length() < 2) return List.of();
        return repository.findByNameContainingIgnoreCaseAndMetadataUpdatedAtIsNotNull(
                query.trim(), org.springframework.data.domain.PageRequest.of(0,20))
                .stream().map(g -> new GameFinderSearchResponse(g.getSteamAppId(),g.getName(),g.getHeaderImageUrl())).toList();
    }
    public List<GameFinderRecommendationResponse> recommend(GameFinderRecommendRequest request) {
        if (request.priceMin()>request.priceMax() || request.playerMin()>request.playerMax())
            throw new IllegalArgumentException("최소 범위는 최대 범위보다 클 수 없습니다.");
        var liked = repository.findBySteamAppIdIn(request.likedSteamAppIds());
        if (liked.size()!=new HashSet<>(request.likedSteamAppIds()).size())
            throw new IllegalArgumentException("선택한 Steam 게임 정보를 찾을 수 없습니다.");
        Set<String> taste = new LinkedHashSet<>(); liked.forEach(g -> taste.addAll(g.features()));
        Set<Long> excluded = new HashSet<>(request.likedSteamAppIds());
        if (request.excludeAppIds()!=null) excluded.addAll(request.excludeAppIds());
        List<Scored> scored = repository.findRecommendationCandidates().stream()
                .filter(g -> !excluded.contains(g.getSteamAppId()))
                .filter(g -> hardFilter.matches(g,request))
                .map(g -> new Scored(g, similarity(taste,g.features())))
                .filter(s -> s.score >= 0.08).sorted(Comparator.comparingDouble(Scored::score).reversed()).toList();
        return diversify(scored).stream().map(this::response).toList();
    }
    private double similarity(Set<String> a, Set<String> b) {
        if(a.isEmpty()||b.isEmpty()) return 0;
        long intersection=a.stream().filter(b::contains).count();
        return intersection/Math.sqrt((double)a.size()*b.size());
    }
    private List<Scored> diversify(List<Scored> source) {
        if(source.size()<=20) return source;
        List<Scored> result=new ArrayList<>();
        take(result,source,0,Math.max(1,(int)(source.size()*0.35)),14);
        take(result,source,(int)(source.size()*0.35),Math.max(1,(int)(source.size()*0.75)),4);
        take(result,source,(int)(source.size()*0.75),source.size(),2);
        return result;
    }
    private void take(List<Scored> out,List<Scored> all,int from,int to,int count){
        if(from>=all.size())return; var bucket=new ArrayList<>(all.subList(from,Math.min(to,all.size())));
        Collections.shuffle(bucket); bucket.stream().limit(count).forEach(out::add);
    }
    private GameFinderRecommendationResponse response(Scored s){var g=s.game;
        return new GameFinderRecommendationResponse(g.getSteamAppId(),g.getName(),g.getHeaderImageUrl(),
                (int)Math.round(s.score*100),g.getPriceCurrent(),g.getPriceOriginal(),g.getDiscountPercent(),
                g.getPriceCurrency(),g.getIsFree(),g.getReleaseDate(),g.getReleaseDateText(),g.isComingSoon(),
                g.getSinglePlayer(),g.getMultiplayer(),g.getOnlineCoop(),g.getMaxPlayers(),
                new ArrayList<>(g.genreSet()),"https://store.steampowered.com/app/"+g.getSteamAppId());}
    private record Scored(SteamGame game,double score){}
}
