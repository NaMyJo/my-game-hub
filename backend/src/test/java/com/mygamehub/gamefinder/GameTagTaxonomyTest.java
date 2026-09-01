package com.mygamehub.gamefinder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class GameTagTaxonomyTest {
 @Test void aliasesNormalize(){var t=new GameTagTaxonomy();for(String v:List.of("협동","코옵","co-op"))assertEquals("coop",t.normalize(v).orElseThrow());for(String v:List.of("멀티","멀티플레이어"))assertEquals("multiplayer",t.normalize(v).orElseThrow());for(String v:List.of("AOS","MOBA"))assertEquals("moba",t.normalize(v).orElseThrow());}
 @Test void steamCategoriesWorkWithoutIgdb(){var g=new SteamGame(10,"A",0,0);g.updateStoreDetail("game",null,null,false,"KRW",0,0,0,0,"NON_ADULT",null,null,false,false,Set.of("생존"),Set.of("협동","멀티플레이어"),true,true,true,false);assertEquals(Set.of("survival","coop","multiplayer"),new GameTagTaxonomy().fromSteam(g));}
}
