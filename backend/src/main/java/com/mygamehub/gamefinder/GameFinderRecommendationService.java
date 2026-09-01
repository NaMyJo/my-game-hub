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
        return searchPage(query, 0, 20).items();
    }
    public GameFinderPageResponse<GameFinderSearchResponse> searchPage(String query, int page, int size) {
        if (query == null || query.trim().length() < 2) {
            return new GameFinderPageResponse<>(List.of(), page, size, false);
        }
        int offset = page * size;
        var values = repository.findActiveByName(query.trim(),
                        org.springframework.data.domain.PageRequest.of(0, offset + size + 1))
                .stream().map(g -> new GameFinderSearchResponse(
                        g.getSteamAppId(), g.getName(), g.getHeaderImageUrl()))
                .skip(offset).toList();
        return GameFinderPageResponse.from(values, page, size);
    }
    public List<GameFinderRecommendationResponse> recommend(GameFinderRecommendRequest request) {
        return recommendPage(request, 0, 20).items();
    }
    public GameFinderPageResponse<GameFinderRecommendationResponse> recommendPage(
            GameFinderRecommendRequest request, int page, int size) {
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
                .filter(SteamGame::isDiscoverable)
                .filter(g -> hardFilter.matches(g,request))
                .map(g -> new Scored(g, similarity(taste,g.features())))
                .filter(s -> s.score >= 0.08).sorted(Comparator.comparingDouble(Scored::score).reversed()).toList();
        int offset = page * size;
        List<GameFinderRecommendationResponse> candidates = diversify(scored)
                .stream().skip(offset).limit(size + 1L).map(this::response).toList();
        return GameFinderPageResponse.from(candidates, page, size);
    }
    private double similarity(Set<String> a, Set<String> b) {
        if(a.isEmpty()||b.isEmpty()) return 0;
        long intersection=a.stream().filter(b::contains).count();
        return intersection/Math.sqrt((double)a.size()*b.size());
    }
    private List<Scored> diversify(List<Scored> source) {
        if(source.size()<=1) return source;
        int highEnd=Math.max(1,(int)(source.size()*0.35));
        int mediumEnd=Math.max(highEnd,(int)(source.size()*0.75));
        List<Scored> high=new ArrayList<>(source.subList(0,highEnd));
        List<Scored> medium=new ArrayList<>(source.subList(highEnd,mediumEnd));
        List<Scored> discovery=new ArrayList<>(source.subList(mediumEnd,source.size()));
        Comparator<Scored> stable=Comparator.comparingLong(value->value.game().getSteamAppId());
        high.sort(stable); medium.sort(stable); discovery.sort(stable);
        List<Scored> result=new ArrayList<>(source.size());
        int highIndex=0,mediumIndex=0,discoveryIndex=0;
        while(result.size()<source.size()){
            for(int i=0;i<7&&highIndex<high.size();i++)result.add(high.get(highIndex++));
            for(int i=0;i<2&&mediumIndex<medium.size();i++)result.add(medium.get(mediumIndex++));
            if(discoveryIndex<discovery.size())result.add(discovery.get(discoveryIndex++));
            if(highIndex>=high.size()&&mediumIndex>=medium.size()&&discoveryIndex>=discovery.size())break;
        }
        return result;
    }
    private GameFinderRecommendationResponse response(Scored s){var g=s.game;
        return new GameFinderRecommendationResponse(g.getSteamAppId(),g.getName(),g.getHeaderImageUrl(),
                (int)Math.round(s.score*100),g.getPriceCurrent(),g.getPriceOriginal(),g.getDiscountPercent(),
                g.getPriceCurrency(),g.getIsFree(),g.getReleaseDate(),g.getReleaseDateText(),g.isComingSoon(),
                g.getSinglePlayer(),g.getMultiplayer(),g.getOnlineCoop(),g.getMaxPlayers(),
                new ArrayList<>(g.genreSet()),"https://store.steampowered.com/app/"+g.getSteamAppId());}
    private record Scored(SteamGame game,double score){}
}
