package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

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
    private final JdbcTemplate jdbc;

    @Autowired
    public GameTagService(GameTagTaxonomy taxonomy, GameTagRepository tags,
            SteamGameTagRepository relations, SteamGameRepository games,
            IgdbRawTaxonomyPersistenceService rawTaxonomy, JdbcTemplate jdbc) {
        this.taxonomy = taxonomy;
        this.tags = tags;
        this.relations = relations;
        this.games = games;
        this.rawTaxonomy = rawTaxonomy;
        this.jdbc = jdbc;
    }

    GameTagService(GameTagTaxonomy taxonomy, GameTagRepository tags,
            SteamGameTagRepository relations, SteamGameRepository games) {
        this.taxonomy = taxonomy;
        this.tags = tags;
        this.relations = relations;
        this.games = games;
        this.rawTaxonomy = null;
        this.jdbc = null;
    }

    GameTagService(GameTagTaxonomy taxonomy, GameTagRepository tags,
            SteamGameTagRepository relations, SteamGameRepository games,
            IgdbRawTaxonomyPersistenceService rawTaxonomy) {
        this.taxonomy = taxonomy; this.tags = tags; this.relations = relations;
        this.games = games; this.rawTaxonomy = rawTaxonomy; this.jdbc = null;
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

    @Transactional
    public Map<Long, Set<String>> rebuildWithIgdbBatch(
            Map<SteamGame, List<IgdbTaxonomyValue>> valuesByGame) {
        if (valuesByGame.isEmpty()) return Map.of();
        if (jdbc == null) {
            Map<Long, Set<String>> result = new LinkedHashMap<>();
            valuesByGame.forEach((game, values) -> result.put(game.getSteamAppId(),
                    rebuildMerged(game, values, values != null)));
            return result;
        }
        Map<Long, DesiredTags> desiredByApp = new LinkedHashMap<>();
        Set<String> allNames = new LinkedHashSet<>();
        valuesByGame.forEach((game, values) -> {
            Set<String> steam = taxonomy.fromSteam(game);
            Set<String> igdb = taxonomy.fromIgdb(values == null ? List.of() : values);
            Map<String, String> sources = new LinkedHashMap<>();
            steam.forEach(name -> sources.put(name, "STEAM_METADATA"));
            igdb.forEach(name -> sources.merge(name, "IGDB_TAXONOMY",
                    (left, right) -> "STEAM_AND_IGDB"));
            desiredByApp.put(game.getSteamAppId(), new DesiredTags(game, sources));
            allNames.addAll(sources.keySet());
        });
        if (!allNames.isEmpty()) {
            jdbc.batchUpdate("insert into game_tags (canonical_name,display_name_ko,type) values (?,?,?) "
                            + "on conflict (canonical_name) do update set "
                            + "display_name_ko=excluded.display_name_ko,type=excluded.type",
                    allNames, allNames.size(), (statement, name) -> {
                        statement.setString(1, name);
                        statement.setString(2, taxonomy.display(name));
                        statement.setString(3, taxonomy.type(name));
                    });
        }
        Map<String, GameTag> tagsByName = new HashMap<>();
        if (!allNames.isEmpty()) tags.findByCanonicalNameIn(allNames)
                .forEach(tag -> tagsByName.put(tag.getCanonicalName(), tag));
        Set<Long> appIds = desiredByApp.keySet();
        Map<TagRelationKey, SteamGameTag> existing = new LinkedHashMap<>();
        relations.findBySteamAppIds(appIds).forEach(relation -> existing.putIfAbsent(
                new TagRelationKey(relation.getSteamAppId(), relation.getTag().getCanonicalName()), relation));
        Map<TagRelationKey, String> desired = new LinkedHashMap<>();
        desiredByApp.forEach((appId, value) -> value.sources().forEach((name, source) ->
                desired.put(new TagRelationKey(appId, name), source)));
        List<Long> obsolete = existing.entrySet().stream()
                .filter(entry -> !desired.containsKey(entry.getKey())
                        || !java.util.Objects.equals(entry.getValue().getSource(), desired.get(entry.getKey())))
                .map(entry -> entry.getValue().getId()).filter(java.util.Objects::nonNull).toList();
        if (!obsolete.isEmpty()) jdbc.batchUpdate("delete from steam_game_tags where id=?", obsolete,
                obsolete.size(), (statement, id) -> statement.setLong(1, id));
        List<Map.Entry<TagRelationKey, String>> additions = desired.entrySet().stream()
                .filter(entry -> !existing.containsKey(entry.getKey())
                        || !java.util.Objects.equals(existing.get(entry.getKey()).getSource(), entry.getValue()))
                .toList();
        if (!additions.isEmpty()) jdbc.batchUpdate(
                "insert into steam_game_tags (steam_app_id,tag_id,source) values (?,?,?) "
                        + "on conflict (steam_app_id,tag_id) do update set source=excluded.source",
                additions, additions.size(), (statement, entry) -> {
                    statement.setLong(1, entry.getKey().steamAppId());
                    statement.setLong(2, tagsByName.get(entry.getKey().canonicalName()).getId());
                    statement.setString(3, entry.getValue());
                });
        desiredByApp.values().forEach(value -> markVersions(value.game(), true));
        Map<Long, Set<String>> result = new LinkedHashMap<>();
        desiredByApp.forEach((appId, value) -> result.put(appId, Set.copyOf(value.sources().keySet())));
        return result;
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

        markVersions(game, igdbTaxonomyApplied);
        games.save(game);
        return names;
    }

    private void markVersions(SteamGame game, boolean igdbTaxonomyApplied) {
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
    }

    private record DesiredTags(SteamGame game, Map<String, String> sources) {}
    private record TagRelationKey(long steamAppId, String canonicalName) {}
}
