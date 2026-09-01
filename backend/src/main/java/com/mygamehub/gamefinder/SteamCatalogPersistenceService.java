package com.mygamehub.gamefinder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SteamCatalogPersistenceService {
    private final SteamGameRepository games;

    public SteamCatalogPersistenceService(SteamGameRepository games) {
        this.games = games;
    }

    @Transactional
    public List<SteamGame> upsertAll(Collection<SteamCatalogClient.CatalogItem> items) {
        if (items.isEmpty()) return List.of();
        List<Long> appIds = items.stream()
                .map(SteamCatalogClient.CatalogItem::appId)
                .distinct()
                .toList();
        Map<Long, SteamGame> existingByAppId = new LinkedHashMap<>();
        games.findBySteamAppIdIn(appIds)
                .forEach(game -> existingByAppId.put(game.getSteamAppId(), game));

        Map<Long, SteamGame> upserts = new LinkedHashMap<>();
        for (SteamCatalogClient.CatalogItem item : items) {
            SteamGame game = existingByAppId.get(item.appId());
            if (game == null) {
                game = new SteamGame(item.appId(), item.name(), item.lastModified(),
                        item.priceChangeNumber());
            }
            game.updateCatalog(item.name(), item.lastModified(), item.priceChangeNumber());
            upserts.put(item.appId(), game);
        }
        return games.saveAll(upserts.values());
    }
}
