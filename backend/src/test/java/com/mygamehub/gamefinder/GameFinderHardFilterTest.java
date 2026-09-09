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
        assertThat(filter.playersMatch(game(false,0,0,1,8),4,6)).isTrue();
        assertThat(filter.playersMatch(game(false,0,0,2,10),4,6)).isTrue();
        assertThat(filter.playersMatch(game(false,0,0,4,6),4,6)).isTrue();
        assertThat(filter.playersMatch(game(false,0,0,1,2),4,6)).isFalse();
        assertThat(filter.playersMatch(game(false,0,0,8,12),4,6)).isFalse();
        assertThat(filter.playersMatch(game(false,0,0,null,null),4,6)).isFalse();
        assertThat(filter.playersMatch(game(false,0,0,null,null),1,15)).isTrue();
    }
    @Test void playerFilterUsesKnownOnlineCapacityForLegacyRows(){
        var legacy=game(false,0,0,null,null);
        legacy.updateIgdb(1L,null,null,8,6,true,true,false);
        assertThat(filter.playersMatch(legacy,4,6)).isTrue();
    }
    @Test void explicitPlayModeSeparatesSingleFromMultiAndKeepsUnknownCapacityForUnrestrictedMulti(){
        var singleOnly=game(false,0,0,null,null);
        var multiUnknown=game(false,0,0,null,null);
        multiUnknown.updateStoreDetail("game",null,null,false,"KRW",0,0,0,null,
                "UNKNOWN",null,null,false,false,Set.of(),Set.of(),false,true,false,false);
        assertThat(filter.playersMatch(singleOnly,1,15,PlayMode.SINGLE)).isTrue();
        assertThat(filter.playersMatch(multiUnknown,1,15,PlayMode.MULTI)).isTrue();
        assertThat(filter.playersMatch(multiUnknown,4,6,PlayMode.MULTI)).isFalse();
    }
    @Test void explicitPriceModeUsesFreeFlagOrKnownCurrentPaidPrice(){
        assertThat(filter.priceMatches(game(true,null,null,null,null),20000,41000,
                PriceMode.FREE)).isTrue();
        assertThat(filter.priceMatches(game(false,50000,30000,null,null),20000,41000,
                PriceMode.PAID)).isTrue();
        assertThat(filter.priceMatches(game(false,50000,null,null,null),20000,41000,
                PriceMode.PAID)).isFalse();
    }
    @Test void adultUnknownIsAllowedButReliableAdultIsExcluded(){
        var request=new GameFinderRecommendRequest(List.of(1L),List.of(),0,100000,false,1,15,List.of());
        var adult=game(false,0,0,null,null);adult.updateStoreDetail("game",null,null,false,"KRW",0,0,0,18,"ADULT",null,null,false,false,Set.of(),Set.of(),null,null,null,null);
        assertThat(filter.matches(adult,request)).isFalse();
        assertThat(filter.adultMatches(game(false,0,0,null,null),false)).isTrue();
    }
    private SteamGame game(boolean free,Integer original,Integer current,Integer min,Integer max){var g=new SteamGame(1,"test",0,0);g.updateStoreDetail("game",null,null,free,"KRW",original,current,0,null,"UNKNOWN",null,null,false,false,Set.of("Action"),Set.of(),true,max!=null,false,false);if(min!=null||max!=null)g.updateIgdb(1L,min,max,max,null,max!=null,false,false);return g;}
}
