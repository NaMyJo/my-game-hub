package com.mygamehub.gamefinder;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SteamCatalogPersistenceService {
    private static final String INSERT_PREFIX = """
            INSERT INTO steam_games
                (steam_app_id, name, store_type, steam_last_modified,
                 steam_price_change_number, adult_status, coming_soon)
            VALUES
            """;
    private static final String UPSERT_SUFFIX = """
            ON CONFLICT (steam_app_id) DO UPDATE SET
                name = EXCLUDED.name,
                steam_last_modified = EXCLUDED.steam_last_modified,
                price_updated_at = CASE
                    WHEN steam_games.steam_price_change_number IS NOT NULL
                     AND steam_games.steam_price_change_number
                         <> EXCLUDED.steam_price_change_number
                    THEN NULL
                    ELSE steam_games.price_updated_at
                END,
                steam_price_change_number = EXCLUDED.steam_price_change_number
            """;

    private final JdbcTemplate jdbc;
    private final SteamGameRepository games;

    public SteamCatalogPersistenceService(JdbcTemplate jdbc, SteamGameRepository games) {
        this.jdbc = jdbc;
        this.games = games;
    }

    @Transactional
    public List<SteamGame> upsertAll(Collection<SteamCatalogClient.CatalogItem> items) {
        Map<Long, SteamCatalogClient.CatalogItem> uniqueItems = new LinkedHashMap<>();
        items.forEach(item -> uniqueItems.put(item.appId(), item));
        if (uniqueItems.isEmpty()) return List.of();

        List<Object> parameters = new ArrayList<>(uniqueItems.size() * 4);
        uniqueItems.values().forEach(item -> {
            parameters.add(item.appId());
            parameters.add(item.name());
            parameters.add(item.lastModified());
            parameters.add(item.priceChangeNumber());
        });
        int affectedRows = jdbc.update(upsertSql(uniqueItems.size()), parameters.toArray());
        if (affectedRows != uniqueItems.size()) {
            throw new IllegalStateException("Steam catalog bulk UPSERT row count mismatch: expected="
                    + uniqueItems.size() + " actual=" + affectedRows);
        }
        return games.findBySteamAppIdIn(uniqueItems.keySet());
    }

    static String upsertSql(int rowCount) {
        if (rowCount < 1) throw new IllegalArgumentException("rowCount must be positive");
        return INSERT_PREFIX
                + String.join(",\n", java.util.Collections.nCopies(
                        rowCount, "(?, ?, 'game', ?, ?, 'UNKNOWN', false)"))
                + "\n" + UPSERT_SUFFIX;
    }
}
