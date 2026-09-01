package com.mygamehub.gamefinder;
import com.mygamehub.gamefinder.dto.GameFinderTagSearchRequest;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class GameFinderTagSearchServiceTest {
 @Test void andTagsReusePriceAndPlayerHardFilter(){var games=mock(SteamGameRepository.class);var rel=mock(SteamGameTagRepository.class);var pass=game(1,8000,1,4);var expensive=game(2,12000,1,4);var unknownPlayers=game(3,8000,null,null);var removed=game(4,8000,1,4);removed.markRemoved();when(rel.findAppIdsMatchingAll(anyCollection(),eq(2L),any(Pageable.class))).thenReturn(List.of(1L,2L,3L,4L));when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of(pass,expensive,unknownPlayers,removed));var req=new GameFinderTagSearchRequest("협동 생존",List.of(),0,10000,false,3,5,0,20);var out=new GameFinderTagSearchService(games,rel,new GameTagTaxonomy(),new GameFinderHardFilter()).search(req);assertEquals(List.of(1L),out.stream().map(v->v.steamAppId()).toList());verify(rel).findAppIdsMatchingAll(argThat(v->v.containsAll(Set.of("coop","survival"))),eq(2L),any());}
 private SteamGame game(long id,Integer price,Integer min,Integer max){var g=new SteamGame(id,"G"+id,0,0);g.updateStoreDetail("game",null,null,false,"KRW",price,price,0,0,"NON_ADULT",null,null,false,false,Set.of(),Set.of(),true,true,false,false);if(min!=null)g.updateIgdb(id,min,max,max,null,true,false,false);return g;}
}
