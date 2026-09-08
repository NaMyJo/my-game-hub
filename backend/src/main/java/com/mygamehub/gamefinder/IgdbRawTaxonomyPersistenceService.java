package com.mygamehub.gamefinder;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class IgdbRawTaxonomyPersistenceService {
    private final IgdbTaxonomyTermRepository terms;
    private final SteamGameIgdbTermRepository relations;
    private final JdbcTemplate jdbc;

    @Autowired
    public IgdbRawTaxonomyPersistenceService(IgdbTaxonomyTermRepository terms,
            SteamGameIgdbTermRepository relations, JdbcTemplate jdbc) {
        this.terms = terms;
        this.relations = relations;
        this.jdbc = jdbc;
    }

    IgdbRawTaxonomyPersistenceService(IgdbTaxonomyTermRepository terms,
            SteamGameIgdbTermRepository relations) {
        this.terms = terms;
        this.relations = relations;
        this.jdbc = null;
    }

    public void syncBatch(Map<SteamGame, List<IgdbTaxonomyValue>> rawByGame) {
        if (rawByGame.isEmpty()) return;
        if (jdbc != null) {
            syncBatchJdbc(rawByGame);
            return;
        }
        Map<TermKey, IgdbTaxonomyValue> requestedTerms = new LinkedHashMap<>();
        rawByGame.values().stream().flatMap(Collection::stream)
                .forEach(value -> requestedTerms.putIfAbsent(TermKey.of(value), value));

        Map<TermKey, IgdbTaxonomyTerm> persistedTerms = new HashMap<>();
        if (!requestedTerms.isEmpty()) {
            Set<IgdbTaxonomySourceType> sourceTypes = new LinkedHashSet<>();
            Set<Long> termIds = new LinkedHashSet<>();
            requestedTerms.keySet().forEach(key -> {
                sourceTypes.add(key.sourceType());
                termIds.add(key.igdbTermId());
            });
            terms.findBySourceTypeInAndIgdbTermIdIn(sourceTypes, termIds).stream()
                    .filter(term -> requestedTerms.containsKey(TermKey.of(term)))
                    .forEach(term -> {
                        TermKey key = TermKey.of(term);
                        term.update(requestedTerms.get(key));
                        persistedTerms.put(key, term);
                    });
            List<IgdbTaxonomyTerm> missing = requestedTerms.entrySet().stream()
                    .filter(entry -> !persistedTerms.containsKey(entry.getKey()))
                    .map(entry -> new IgdbTaxonomyTerm(entry.getValue())).toList();
            terms.saveAll(missing).forEach(term -> persistedTerms.put(TermKey.of(term), term));
        }

        Set<Long> appIds = rawByGame.keySet().stream().map(SteamGame::getSteamAppId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<SteamGameIgdbTerm> existing = relations.findBySteamAppIds(appIds);
        Map<RelationKey, SteamGameIgdbTerm> existingByKey = new LinkedHashMap<>();
        existing.forEach(relation -> existingByKey.putIfAbsent(RelationKey.of(relation), relation));

        Map<Long, SteamGame> gamesById = new LinkedHashMap<>();
        rawByGame.keySet().forEach(game -> gamesById.put(game.getSteamAppId(), game));
        Set<RelationKey> desired = new LinkedHashSet<>();
        rawByGame.forEach((game, values) -> values.stream().map(TermKey::of).distinct()
                .filter(persistedTerms::containsKey)
                .forEach(key -> desired.add(new RelationKey(game.getSteamAppId(), key))));

        List<SteamGameIgdbTerm> obsolete = existingByKey.entrySet().stream()
                .filter(entry -> !desired.contains(entry.getKey())).map(Map.Entry::getValue).toList();
        if (!obsolete.isEmpty()) relations.deleteAll(obsolete);
        List<SteamGameIgdbTerm> additions = new ArrayList<>();
        for (RelationKey key : desired) {
            if (!existingByKey.containsKey(key)) {
                additions.add(new SteamGameIgdbTerm(gamesById.get(key.steamAppId()),
                        persistedTerms.get(key.termKey())));
            }
        }
        if (!additions.isEmpty()) relations.saveAll(additions);
    }

    private void syncBatchJdbc(Map<SteamGame, List<IgdbTaxonomyValue>> rawByGame) {
        Map<TermKey, IgdbTaxonomyValue> requestedTerms = new LinkedHashMap<>();
        rawByGame.values().stream().flatMap(Collection::stream)
                .forEach(value -> requestedTerms.putIfAbsent(TermKey.of(value), value));
        if (!requestedTerms.isEmpty()) {
            jdbc.batchUpdate("insert into igdb_taxonomy_terms "
                            + "(source_type,igdb_term_id,name,slug,updated_at) values (?,?,?,?,CURRENT_TIMESTAMP) "
                            + "on conflict (source_type,igdb_term_id) do update set "
                            + "name=excluded.name,slug=excluded.slug,updated_at=excluded.updated_at",
                    requestedTerms.values(), requestedTerms.size(), (statement, value) -> {
                        statement.setObject(1, value.sourceType().name(), java.sql.Types.OTHER);
                        statement.setLong(2, value.igdbTermId());
                        statement.setString(3, value.name() == null ? "" : value.name());
                        statement.setString(4, value.slug() == null ? "" : value.slug());
                    });
        }
        Set<IgdbTaxonomySourceType> sourceTypes = new LinkedHashSet<>();
        Set<Long> termIds = new LinkedHashSet<>();
        requestedTerms.keySet().forEach(key -> {
            sourceTypes.add(key.sourceType());
            termIds.add(key.igdbTermId());
        });
        Map<TermKey, IgdbTaxonomyTerm> persistedTerms = new HashMap<>();
        if (!requestedTerms.isEmpty()) {
            terms.findBySourceTypeInAndIgdbTermIdIn(sourceTypes, termIds).stream()
                    .filter(term -> requestedTerms.containsKey(TermKey.of(term)))
                    .forEach(term -> persistedTerms.put(TermKey.of(term), term));
        }

        Set<Long> appIds = rawByGame.keySet().stream().map(SteamGame::getSteamAppId)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        Map<RelationKey, SteamGameIgdbTerm> existingByKey = new LinkedHashMap<>();
        relations.findBySteamAppIds(appIds).forEach(relation ->
                existingByKey.putIfAbsent(RelationKey.of(relation), relation));
        Set<RelationKey> desired = new LinkedHashSet<>();
        rawByGame.forEach((game, values) -> values.stream().map(TermKey::of).distinct()
                .filter(persistedTerms::containsKey)
                .forEach(key -> desired.add(new RelationKey(game.getSteamAppId(), key))));
        List<Long> obsoleteIds = existingByKey.entrySet().stream()
                .filter(entry -> !desired.contains(entry.getKey()))
                .map(entry -> entry.getValue().getId()).filter(java.util.Objects::nonNull).toList();
        if (!obsoleteIds.isEmpty()) {
            jdbc.batchUpdate("delete from steam_game_igdb_terms where id=?", obsoleteIds,
                    obsoleteIds.size(), (statement, id) -> statement.setLong(1, id));
        }
        List<RelationKey> additions = desired.stream()
                .filter(key -> !existingByKey.containsKey(key)).toList();
        if (!additions.isEmpty()) {
            jdbc.batchUpdate("insert into steam_game_igdb_terms (steam_app_id,taxonomy_term_id) "
                            + "values (?,?) on conflict (steam_app_id,taxonomy_term_id) do nothing",
                    additions, additions.size(), (statement, key) -> {
                        statement.setLong(1, key.steamAppId());
                        statement.setLong(2, persistedTerms.get(key.termKey()).getId());
                    });
        }
    }

    public List<IgdbTaxonomyValue> valuesForGame(long steamAppId) {
        return relations.findBySteamAppId(steamAppId).stream().map(SteamGameIgdbTerm::getTaxonomyTerm)
                .map(term -> new IgdbTaxonomyValue(term.getSourceType(), term.getIgdbTermId(),
                        term.getName(), term.getSlug()))
                .toList();
    }

    private record TermKey(IgdbTaxonomySourceType sourceType, long igdbTermId) {
        static TermKey of(IgdbTaxonomyValue value) {
            return new TermKey(value.sourceType(), value.igdbTermId());
        }
        static TermKey of(IgdbTaxonomyTerm term) {
            return new TermKey(term.getSourceType(), term.getIgdbTermId());
        }
    }
    private record RelationKey(long steamAppId, TermKey termKey) {
        static RelationKey of(SteamGameIgdbTerm relation) {
            return new RelationKey(relation.getGame().getSteamAppId(),
                    TermKey.of(relation.getTaxonomyTerm()));
        }
    }
}
