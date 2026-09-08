package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GameTagService {
    private final GameTagTaxonomy taxonomy;
    private final GameTagRepository tags;
    private final SteamGameTagRepository relations;
    private final SteamGameRepository games;
    private final IgdbRawTaxonomyPersistenceService rawTaxonomy;

    @Autowired
    public GameTagService(GameTagTaxonomy taxonomy, GameTagRepository tags,
            SteamGameTagRepository relations, SteamGameRepository games,
            IgdbRawTaxonomyPersistenceService rawTaxonomy) {
        this.taxonomy = taxonomy;
        this.tags = tags;
        this.relations = relations;
        this.games = games;
        this.rawTaxonomy = rawTaxonomy;
    }

    GameTagService(GameTagTaxonomy taxonomy, GameTagRepository tags,
            SteamGameTagRepository relations, SteamGameRepository games) {
        this.taxonomy = taxonomy;
        this.tags = tags;
        this.relations = relations;
        this.games = games;
        this.rawTaxonomy = null;
    }

    @Transactional
    public Set<String> rebuild(SteamGame game) {
        List<IgdbTaxonomyValue> igdbValues = rawTaxonomy == null
                ? List.of() : rawTaxonomy.valuesForGame(game.getSteamAppId());
        return rebuildMerged(game, igdbValues, false);
    }

    @Transactional
    public Set<String> rebuildWithIgdb(SteamGame game, List<IgdbTaxonomyValue> igdbValues) {
        return rebuildMerged(game, igdbValues, true);
    }

    private Set<String> rebuildMerged(SteamGame game, List<IgdbTaxonomyValue> igdbValues,
            boolean igdbTaxonomyApplied) {
        Set<String> steamNames = taxonomy.fromSteam(game);
        Set<String> igdbNames = taxonomy.fromIgdb(igdbValues);
        Set<String> names = new LinkedHashSet<>(steamNames);
        names.addAll(igdbNames);

        Map<String, GameTag> found = new HashMap<>();
        tags.findByCanonicalNameIn(names).forEach(tag -> {
            tag.updatePresentation(taxonomy.display(tag.getCanonicalName()),
                    taxonomy.type(tag.getCanonicalName()));
            found.put(tag.getCanonicalName(), tag);
        });
        for (String name : names) {
            found.computeIfAbsent(name, key -> tags.save(new GameTag(key,
                    taxonomy.display(key), taxonomy.type(key))));
        }

        relations.deleteBySteamAppId(game.getSteamAppId());
        Map<String, String> sources = new LinkedHashMap<>();
        steamNames.forEach(name -> sources.put(name, "STEAM_METADATA"));
        igdbNames.forEach(name -> sources.merge(name, "IGDB_TAXONOMY",
                (left, right) -> "STEAM_AND_IGDB"));
        relations.saveAll(names.stream().map(name -> new SteamGameTag(
                game.getSteamAppId(), found.get(name), sources.get(name))).toList());

        game.markSteamTaxonomyVersion(GameTagTaxonomy.STEAM_VERSION);
        if (igdbTaxonomyApplied) game.markIgdbTaxonomyVersion(GameTagTaxonomy.IGDB_VERSION);
        boolean igdbTerminalWithoutTerms = game.getIgdbStatus() == EnrichmentStatus.NOT_FOUND
                || game.getIgdbStatus() == EnrichmentStatus.PERMANENT_FAILURE;
        if (igdbTerminalWithoutTerms) {
            game.markIgdbTaxonomyVersion(GameTagTaxonomy.IGDB_VERSION);
        }
        boolean completeIgdbTaxonomy = GameTagTaxonomy.IGDB_VERSION.equals(game.getIgdbTaxonomyVersion());
        game.markTaxonomyVersion((igdbTerminalWithoutTerms || completeIgdbTaxonomy)
                ? GameTagTaxonomy.CURRENT_VERSION : GameTagTaxonomy.STEAM_VERSION);
        games.save(game);
        return names;
    }
}
