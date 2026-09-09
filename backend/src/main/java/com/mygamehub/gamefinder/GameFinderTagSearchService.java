package com.mygamehub.gamefinder;
import com.mygamehub.gamefinder.dto.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.*;
@Service
public class GameFinderTagSearchService {
    private final SteamGameRepository games;private final SteamGameTagRepository relations;private final GameTagTaxonomy taxonomy;
    public GameFinderTagSearchService(SteamGameRepository games,SteamGameTagRepository relations,GameTagTaxonomy taxonomy){this.games=games;this.relations=relations;this.taxonomy=taxonomy;}
    public List<GameFinderTagSearchResponse> search(GameFinderTagSearchRequest request){return searchPage(request).items();}
    public GameFinderPageResponse<GameFinderTagSearchResponse> searchPage(GameFinderTagSearchRequest request){
        if(request.priceMin()>request.priceMax()||request.playerMin()>request.playerMax())throw new IllegalArgumentException("invalid filter range");
        Set<String> requested=taxonomy.parse(request.query(),request.tags());
        if(requested.isEmpty())return new GameFinderPageResponse<>(List.of(),request.page(),request.size(),false);
        boolean priceUnrestricted=request.priceMin()==0&&request.priceMax()==100000;
        boolean playersUnrestricted=request.playerMin()==1&&request.playerMax()==15;
        List<Long> ids;
        if(request.playMode()==null&&request.priceMode()==null){
            ids=relations.findFilteredAppIdsMatchingAll(requested,requested.size(),request.priceMin(),request.priceMax(),priceUnrestricted,request.includeAdult(),request.playerMin(),request.playerMax(),playersUnrestricted,PageRequest.of(request.page(),request.size()+1));
        }else{
            ids=relations.findFilteredAppIdsMatchingAll(requested,requested.size(),request.priceMin(),request.priceMax(),priceUnrestricted,request.includeAdult(),request.playerMin(),request.playerMax(),playersUnrestricted,request.playMode()==null?null:request.playMode().name(),request.priceMode()==null?null:request.priceMode().name(),PageRequest.of(request.page(),request.size()+1));
        }
        Map<Long,SteamGame> byId=new HashMap<>();games.findBySteamAppIdIn(ids).forEach(g->byId.put(g.getSteamAppId(),g));
        List<GameFinderTagSearchResponse> values=ids.stream().map(byId::get).filter(Objects::nonNull)
                .map(g->response(g,requested)).toList();
        return GameFinderPageResponse.from(values,request.page(),request.size());
    }
    private GameFinderTagSearchResponse response(SteamGame g,Set<String> tags){return new GameFinderTagSearchResponse(g.getSteamAppId(),g.getName(),g.getHeaderImageUrl(),g.getPriceCurrent(),g.getPriceOriginal(),g.getDiscountPercent(),g.getIsFree(),g.getMultiplayer(),g.getOnlineCoop(),g.getMinPlayers(),g.getMaxPlayers(),g.isComingSoon(),new ArrayList<>(tags),"https://store.steampowered.com/app/"+g.getSteamAppId());}
}
