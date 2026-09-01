package com.mygamehub.gamefinder;
import com.mygamehub.gamefinder.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class GameFinderTagSearchService {
    private final SteamGameRepository games;private final SteamGameTagRepository relations;private final GameTagTaxonomy taxonomy;private final GameFinderHardFilter filter;
    public GameFinderTagSearchService(SteamGameRepository games,SteamGameTagRepository relations,GameTagTaxonomy taxonomy,GameFinderHardFilter filter){this.games=games;this.relations=relations;this.taxonomy=taxonomy;this.filter=filter;}
    public List<GameFinderTagSearchResponse> search(GameFinderTagSearchRequest request){return searchPage(request).items();}
    public GameFinderPageResponse<GameFinderTagSearchResponse> searchPage(GameFinderTagSearchRequest request){
        if(request.priceMin()>request.priceMax()||request.playerMin()>request.playerMax())throw new IllegalArgumentException("invalid filter range");
        Set<String> requested=taxonomy.parse(request.query(),request.tags());
        if(requested.isEmpty())return new GameFinderPageResponse<>(List.of(),request.page(),request.size(),false);
        int offset=request.page()*request.size();
        int candidateLimit=Math.min(10000,Math.max(request.size()+1,(offset+request.size()+1)*3));
        List<Long> ids=relations.findAppIdsMatchingAll(requested,requested.size(),PageRequest.of(0,candidateLimit));
        Map<Long,SteamGame> byId=new HashMap<>();games.findBySteamAppIdIn(ids).forEach(g->byId.put(g.getSteamAppId(),g));
        List<GameFinderTagSearchResponse> values=ids.stream().map(byId::get).filter(Objects::nonNull)
                .filter(g->filter.matches(g,request)).skip(offset).limit(request.size()+1L)
                .map(g->response(g,requested)).toList();
        return GameFinderPageResponse.from(values,request.page(),request.size());
    }
    private GameFinderTagSearchResponse response(SteamGame g,Set<String> tags){return new GameFinderTagSearchResponse(g.getSteamAppId(),g.getName(),g.getHeaderImageUrl(),g.getPriceCurrent(),g.getPriceOriginal(),g.getDiscountPercent(),g.getIsFree(),g.getMultiplayer(),g.getOnlineCoop(),g.getMinPlayers(),g.getMaxPlayers(),g.isComingSoon(),new ArrayList<>(tags),"https://store.steampowered.com/app/"+g.getSteamAppId());}
}
