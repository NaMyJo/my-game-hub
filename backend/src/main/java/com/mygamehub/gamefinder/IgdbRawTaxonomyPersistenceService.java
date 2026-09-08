package com.mygamehub.gamefinder;

import org.springframework.stereotype.Service;

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

    public IgdbRawTaxonomyPersistenceService(IgdbTaxonomyTermRepository terms,
            SteamGameIgdbTermRepository relations) {
        this.terms = terms;
        this.relations = relations;
    }

    public void syncBatch(Map<SteamGame, List<IgdbTaxonomyValue>> rawByGame) {
        if (rawByGame.isEmpty()) return;
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
