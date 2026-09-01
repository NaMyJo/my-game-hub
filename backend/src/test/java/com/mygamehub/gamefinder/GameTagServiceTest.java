package com.mygamehub.gamefinder;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
class GameTagServiceTest {
 @Test void rebuildCreatesNoDuplicateRelation(){var tags=mock(GameTagRepository.class);var rel=mock(SteamGameTagRepository.class);when(tags.findByCanonicalNameIn(anyCollection())).thenReturn(List.of());when(tags.save(any())).thenAnswer(i->i.getArgument(0));var g=new SteamGame(10,"G",0,0);g.updateStoreDetail("game",null,null,false,"KRW",0,0,0,0,"NON_ADULT",null,null,false,false,Set.of("액션"),Set.of("협동","코옵"),true,true,false,false);var result=new GameTagService(new GameTagTaxonomy(),tags,rel).rebuild(g);assertEquals(Set.of("action","coop"),result);verify(rel).deleteBySteamAppId(10L);verify(rel).saveAll(argThat(v->{var list=new ArrayList<SteamGameTag>();v.forEach(list::add);return list.size()==2;}));}
}
