package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.*;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class GameFinderRecommendationService {
    private final SteamGameRepository repository;
    private final GameFinderHardFilter hardFilter;
    private final GameTagTaxonomy taxonomy;
    public GameFinderRecommendationService(SteamGameRepository repository, GameFinderHardFilter hardFilter,
            GameTagTaxonomy taxonomy) {
        this.repository=repository; this.hardFilter=hardFilter; this.taxonomy=taxonomy;
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
        Set<String> preferred = normalizePreferredTags(request.preferredTags());
        if (request.likedSteamAppIds().isEmpty() && preferred.isEmpty())
            throw new IllegalArgumentException("취향 게임 또는 선호 태그를 하나 이상 선택해주세요.");
        var liked = repository.findBySteamAppIdIn(request.likedSteamAppIds());
        if (liked.size()!=new HashSet<>(request.likedSteamAppIds()).size())
            throw new IllegalArgumentException("선택한 Steam 게임 정보를 찾을 수 없습니다.");
        Set<String> taste = new LinkedHashSet<>(); liked.forEach(g -> taste.addAll(taxonomy.fromSteam(g)));
        Set<Long> excluded = new HashSet<>(request.likedSteamAppIds());
        if (request.excludeAppIds()!=null) excluded.addAll(request.excludeAppIds());
        List<Scored> scored = repository.findRecommendationCandidates().stream()
                .filter(g -> !excluded.contains(g.getSteamAppId()))
                .filter(SteamGame::isDiscoverable)
                .filter(g -> hardFilter.matches(g,request))
                .map(g -> new Scored(g, score(taste, preferred, taxonomy.fromSteam(g))))
                .filter(s -> request.likedSteamAppIds().isEmpty() || s.score >= 0.08)
                .sorted(Comparator.comparingDouble(Scored::score).reversed()
                        .thenComparingLong(s -> s.game().getSteamAppId())).toList();
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
    private double score(Set<String> seedTaste, Set<String> preferred, Set<String> candidate) {
        double seedScore = similarity(seedTaste, candidate);
        double tagScore = preferred.isEmpty() ? 0
                : preferred.stream().filter(candidate::contains).count() / (double) preferred.size();
        if (seedTaste.isEmpty()) return 0.10 + 0.90 * tagScore;
        if (preferred.isEmpty()) return seedScore;
        return 0.80 * seedScore + 0.20 * tagScore;
    }
    Set<String> normalizePreferredTags(List<String> values) {
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            String tag = taxonomy.normalize(value).orElseThrow(
                    () -> new IllegalArgumentException("지원하지 않는 선호 태그입니다: " + value));
            normalized.add(tag);
        }
        return normalized;
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
