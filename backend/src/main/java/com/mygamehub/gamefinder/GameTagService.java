package com.mygamehub.gamefinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class GameTagService {
    private final GameTagTaxonomy taxonomy; private final GameTagRepository tags; private final SteamGameTagRepository relations;
    public GameTagService(GameTagTaxonomy taxonomy,GameTagRepository tags,SteamGameTagRepository relations){this.taxonomy=taxonomy;this.tags=tags;this.relations=relations;}
    @Transactional public Set<String> rebuild(SteamGame game){Set<String> names=taxonomy.fromSteam(game);Map<String,GameTag> found=new HashMap<>();tags.findByCanonicalNameIn(names).forEach(t->found.put(t.getCanonicalName(),t));for(String name:names)found.computeIfAbsent(name,n->tags.save(new GameTag(n,taxonomy.display(n),taxonomy.type(n))));relations.deleteBySteamAppId(game.getSteamAppId());relations.saveAll(names.stream().map(n->new SteamGameTag(game.getSteamAppId(),found.get(n),"STEAM_METADATA")).toList());return names;}
}
