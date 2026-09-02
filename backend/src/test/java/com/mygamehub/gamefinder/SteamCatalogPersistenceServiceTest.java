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
        assertEquals(100, countOccurrences(sql.getValue(), "(?, ?, 'game', ?, ?, 'UNKNOWN', false, 'ACTIVE', CURRENT_TIMESTAMP, ?, ?)"));
        assertTrue(sql.getValue().contains("ON CONFLICT (steam_app_id) DO UPDATE"));
        assertEquals(600, parameters.getValue().length);
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
        assertArrayEquals(new Object[]{10L, "Latest", 2L, 2L, null, null}, parameters.getValue());
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

    @Test
    void gameOnlyUpsertMarksEligibilityWithoutOverwritingItInGeneralScans() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of());
        var item = new SteamCatalogClient.CatalogItem(10, "Game", 1, 1);

        var service = new SteamCatalogPersistenceService(jdbc, games);
        service.upsertGameCatalogAll(List.of(item));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> args = ArgumentCaptor.forClass(Object[].class);
        verify(jdbc).update(sql.capture(), args.capture());
        assertEquals(true, args.getValue()[5]);
        assertTrue(sql.getValue().contains("game_catalog_eligible = COALESCE"));
    }

    @Test
    void igdbBatchPersistenceLoadsAllTargetsOnceWithoutPerGameSave() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        SteamGame first = new SteamGame(570, "Dota 2", 1, 1);
        SteamGame second = new SteamGame(1245620, "ELDEN RING", 1, 1);
        when(games.findBySteamAppIdIn(anyCollection()))
                .thenReturn(List.of(first, second));
        var values = java.util.Map.of(
                570L, java.util.Optional.of(new IgdbEnrichmentClient.IgdbData(
                        42L, 1, 1, 10, 10, 5, true, true, false)),
                1245620L, java.util.Optional.<IgdbEnrichmentClient.IgdbData>empty());

        var result = new SteamCatalogPersistenceService(jdbc, games)
                .applyIgdbResults(List.of(570L, 1245620L), values);

        assertEquals(2, result.size());
        assertEquals(EnrichmentStatus.SUCCESS, first.getIgdbStatus());
        assertEquals(EnrichmentStatus.NOT_FOUND, second.getIgdbStatus());
        verify(games, times(1)).findBySteamAppIdIn(anyCollection());
        verify(games, never()).save(any());
        verify(games, never()).saveAll(anyCollection());
    }

    @Test
    void igdbPersistenceIgnoresUnrequestedRowsAndDeduplicatesRequestedRow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SteamGameRepository games = mock(SteamGameRepository.class);
        SteamGame requested = new SteamGame(10, "Requested", 1, 1);
        SteamGame unrequested = new SteamGame(999, "Unexpected", 1, 1);
        when(games.findBySteamAppIdIn(anyCollection()))
                .thenReturn(List.of(unrequested, requested, requested));
        var values = java.util.Map.of(
                10L, java.util.Optional.of(new IgdbEnrichmentClient.IgdbData(
                        100L, 1, 1, 4, 4, 2, true, true, false)),
                999L, java.util.Optional.of(new IgdbEnrichmentClient.IgdbData(
                        9999L, 1, 1, 99, 99, 99, true, true, true)));

        var result = new SteamCatalogPersistenceService(jdbc, games)
                .applyIgdbResults(List.of(10L, 10L), values);

        assertEquals(1, result.size());
        assertEquals(100L, requested.getIgdbGameId());
        assertNull(unrequested.getIgdbGameId());
        assertNull(unrequested.getIgdbStatus());
    }

    private static int countOccurrences(String value, String token) {
        return (value.length() - value.replace(token, "").length()) / token.length();
    }
}
