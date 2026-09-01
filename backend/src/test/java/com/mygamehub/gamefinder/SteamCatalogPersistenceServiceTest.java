package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

class SteamCatalogPersistenceServiceTest {
    @Test
    void bulkUpsertUsesOneLookupAndOneSaveAllForOneHundredItems() {
        SteamGameRepository games = mock(SteamGameRepository.class);
        SteamGame existing = new SteamGame(10, "Old", 1, 1);
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of(existing));
        when(games.saveAll(anyCollection()))
                .thenAnswer(invocation -> new ArrayList<>((Collection<SteamGame>) invocation.getArgument(0)));
        List<SteamCatalogClient.CatalogItem> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            long appId = 10L + i;
            items.add(new SteamCatalogClient.CatalogItem(appId, "Game " + appId, i, i));
        }

        List<SteamGame> saved = new SteamCatalogPersistenceService(games).upsertAll(items);

        assertEquals(100, saved.size());
        assertEquals("Game 10", existing.getName());
        verify(games, times(1)).findBySteamAppIdIn(anyCollection());
        verify(games, times(1)).saveAll(anyCollection());
        verify(games, never()).findBySteamAppId(anyLong());
        verify(games, never()).save(any());
    }

    @Test
    void duplicateAppIdsProduceOneUpsert() {
        SteamGameRepository games = mock(SteamGameRepository.class);
        when(games.findBySteamAppIdIn(anyCollection())).thenReturn(List.of());
        when(games.saveAll(anyCollection()))
                .thenAnswer(invocation -> new ArrayList<>((Collection<SteamGame>) invocation.getArgument(0)));
        var first = new SteamCatalogClient.CatalogItem(10, "Old", 1, 1);
        var latest = new SteamCatalogClient.CatalogItem(10, "Latest", 2, 2);

        List<SteamGame> saved = new SteamCatalogPersistenceService(games)
                .upsertAll(List.of(first, latest));

        assertEquals(1, saved.size());
        assertEquals("Latest", saved.getFirst().getName());
    }
}
