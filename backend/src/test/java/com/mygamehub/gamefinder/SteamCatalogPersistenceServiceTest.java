package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SteamCatalogPersistenceServiceTest {
    @Test
    void oneHundredItemsUseOneMultiRowUpsertAndOneReloadQuery() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        List<SteamCatalogClient.CatalogItem> items = new ArrayList<>();
        List<SteamGame> stored = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long appId = 10L + i;
            items.add(new SteamCatalogClient.CatalogItem(appId, "Game " + appId, i, i));
            stored.add(new SteamGame(appId, "Game " + appId, i, i));
        }
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(100);
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(stored);

        List<SteamGame> result = new SteamCatalogPersistenceService(jdbc, games)
                .upsertAll(items);

        assertEquals(100, result.size());
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc, times(1)).update(sql.capture(), parameters.capture());
        assertEquals(100, countOccurrences(sql.getValue(), "(?, ?, 'game', ?, ?, 'UNKNOWN', false, 'ACTIVE', CURRENT_TIMESTAMP, ?)"));
        assertTrue(sql.getValue().contains("ON CONFLICT (steam_app_id) DO UPDATE"));
        assertEquals(500, parameters.getValue().length);
        verify(games, times(1)).findBySteamAppIdIn(anyCollection());
        verify(games, never()).save(any());
        verify(games, never()).saveAll(anyCollection());
        verify(games, never()).findBySteamAppId(anyLong());
    }

    @Test
    void duplicateAppIdsProduceOneValuesTupleAndLatestValues() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        SteamGame stored = new SteamGame(10, "Latest", 2, 2);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of(stored));
        var first = new SteamCatalogClient.CatalogItem(10, "Old", 1, 1);
        var latest = new SteamCatalogClient.CatalogItem(10, "Latest", 2, 2);

        List<SteamGame> result = new SteamCatalogPersistenceService(jdbc, games)
                .upsertAll(List.of(first, latest));

        ArgumentCaptor<Object[]> parameters = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(anyString(), parameters.capture());
        assertArrayEquals(new Object[]{10L, "Latest", 2L, 2L, null}, parameters.getValue());
        assertEquals(1, result.size());
    }

    @Test
    void generatedSqlPreservesPriceRefreshSemantics() {
        String sql = SteamCatalogPersistenceService.upsertSql(1);

        assertTrue(sql.contains("price_updated_at = CASE"));
        assertTrue(sql.contains("steam_games.steam_price_change_number IS NOT NULL"));
        assertTrue(sql.contains("THEN NULL"));
        assertTrue(sql.contains("metadata_status = CASE"));
        assertTrue(sql.contains("lifecycle_status = 'ACTIVE'"));
        assertThrows(IllegalArgumentException.class,
                () -> SteamCatalogPersistenceService.upsertSql(0));
    }

    @Test
    void affectedRowMismatchFailsBeforeReload() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(0);
        var item = new SteamCatalogClient.CatalogItem(10, "Game", 1, 1);

        assertThrows(IllegalStateException.class,
                () -> new SteamCatalogPersistenceService(jdbc, games).upsertAll(List.of(item)));
        verify(games, never()).findBySteamAppIdIn(anyCollection());
    }

    @Test
    void reconciliationGenerationIsBoundAndLifecycleIsReactivated() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        SteamGame stored = new SteamGame(10, "Game", 1, 1);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of(stored));
        var item = new SteamCatalogClient.CatalogItem(10, "Game", 1, 1);

        new SteamCatalogPersistenceService(jdbc, games)
                .upsertAll(List.of(item), "generation-1");

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertEquals("generation-1", args.getValue()[4]);
        assertTrue(sql.getValue().contains("lifecycle_status = 'ACTIVE'"));
        assertTrue(sql.getValue().contains("reconciliation_generation = COALESCE"));
    }

    private static int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
