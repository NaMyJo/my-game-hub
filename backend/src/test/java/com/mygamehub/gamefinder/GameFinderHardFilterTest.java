package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderRecommendRequest;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class GameFinderHardFilterTest {
    private final GameFinderHardFilter filter=new GameFinderHardFilter();
    @Test void priceUsesCurrentDiscountedPriceAndHandlesUnknown(){
        assertThat(filter.priceMatches(game(false,32000,8000,null,null),0,10000)).isTrue();
        assertThat(filter.priceMatches(game(false,10001,10001,null,null),0,10000)).isFalse();
        assertThat(filter.priceMatches(game(true,null,null,null,null),0,10000)).isTrue();
        assertThat(filter.priceMatches(game(true,null,null,null,null),1,10000)).isFalse();
        assertThat(filter.priceMatches(game(false,null,null,null,null),0,100000)).isTrue();
        assertThat(filter.priceMatches(game(false,null,null,null,null),0,99999)).isFalse();
    }
    @Test void playerFilterUsesRangeOverlapAndRejectsUnknownWhenRestricted(){
        assertThat(filter.playersMatch(game(false,0,0,1,4),3,5)).isTrue();
        assertThat(filter.playersMatch(game(false,0,0,2,8),3,5)).isTrue();
        assertThat(filter.playersMatch(game(false,0,0,1,2),3,5)).isFalse();
        assertThat(filter.playersMatch(game(false,0,0,6,10),3,5)).isFalse();
        assertThat(filter.playersMatch(game(false,0,0,null,null),3,5)).isFalse();
        assertThat(filter.playersMatch(game(false,0,0,null,null),1,15)).isTrue();
    }
    @Test void adultUnknownIsAllowedButReliableAdultIsExcluded(){
        var request=new GameFinderRecommendRequest(List.of(1L),List.of(),0,100000,false,1,15,List.of());
        var adult=game(false,0,0,null,null);adult.updateStoreDetail("game",null,null,false,"KRW",0,0,0,18,"ADULT",null,null,false,false,Set.of(),Set.of(),null,null,null,null);
        assertThat(filter.matches(adult,request)).isFalse();
        assertThat(filter.adultMatches(game(false,0,0,null,null),false)).isTrue();
    }
    private SteamGame game(boolean free,Integer original,Integer current,Integer min,Integer max){var g=new SteamGame(1,"test",0,0);g.updateStoreDetail("game",null,null,free,"KRW",original,current,0,null,"UNKNOWN",null,null,false,false,Set.of("Action"),Set.of(),true,max!=null,false,false);if(min!=null||max!=null)g.updateIgdb(1L,min,max,max,null,max!=null,false,false);return g;}
}
