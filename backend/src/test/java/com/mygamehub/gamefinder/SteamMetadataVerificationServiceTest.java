package com.mygamehub.gamefinder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SteamMetadataVerificationServiceTest {
    private SteamGameRepository games;
    private SteamStoreDetailClient store;
    private SteamMetadataVerificationService verifier;

    @BeforeEach
    void setUp() {
        games = mock(SteamGameRepository.class);
        store = mock(SteamStoreDetailClient.class);
        verifier = new SteamMetadataVerificationService(games, store);
    }

    @Test
    void exactIdNameAndTypeMatch() {
        SteamGame game = game(570, "Dota 2", 0, 0);
        when(games.findMetadataVerificationRandomSample(any(Pageable.class)))
                .thenReturn(List.of(game));
        when(store.get(570)).thenReturn(Optional.of(detail(570, "Dota 2", 0, 0)));

        var summary = verifier.verify(100,
                SteamMetadataVerificationService.VerificationMode.RANDOM);

        assertEquals(1, summary.matched());
        assertEquals(0, summary.criticalMismatch());
        verify(games, never()).save(any());
        verify(games, never()).saveAll(any());
    }

    @Test
    void responseIdMismatchIsCriticalAndNeverSaved() {
        SteamGame game = game(570, "Dota 2", 0, 0);
        when(games.findMetadataVerificationRandomSample(any(Pageable.class)))
                .thenReturn(List.of(game));
        when(store.get(570)).thenReturn(Optional.of(detail(730, "Counter-Strike 2", 0, 0)));

        var result = verifier.verify(1,
                SteamMetadataVerificationService.VerificationMode.RANDOM).results().get(0);

        assertEquals(SteamMetadataVerificationService.VerificationOutcome.CRITICAL,
                result.outcome());
        assertEquals(List.of("steam_app_id"), result.mismatchedFields());
        verify(games, never()).save(any());
    }

    @Test
    void candidateAndResponseOrderCannotCrossAssignRows() {
        SteamGame a = game(10, "A", 0, 0);
        SteamGame b = game(20, "B", 0, 0);
        SteamGame c = game(30, "C", 0, 0);
        when(games.findMetadataVerificationRandomSample(any(Pageable.class)))
                .thenReturn(List.of(a, b, c));
        // Values are deliberately prepared C/A/B; lookup remains keyed by requested ID.
        var responseC = detail(30, "C", 0, 0);
        var responseA = detail(10, "A", 0, 0);
        var responseB = detail(20, "B", 0, 0);
        when(store.get(anyLong())).thenAnswer(invocation -> switch ((int) invocation.getArgument(0, Long.class).longValue()) {
            case 10 -> Optional.of(responseA);
            case 20 -> Optional.of(responseB);
            case 30 -> Optional.of(responseC);
            default -> Optional.empty();
        });

        var summary = verifier.verify(3,
                SteamMetadataVerificationService.VerificationMode.RANDOM);

        assertEquals(3, summary.matched());
        assertEquals(List.of(10L, 20L, 30L), summary.results().stream()
                .map(SteamMetadataVerificationService.VerificationResult::steamAppId).toList());
    }

    @Test
    void anotherGamesNameIsCriticalButPriceOnlyChangeIsChanged() {
        SteamGame wrongName = game(10, "A", 1000, 900);
        SteamGame priceChanged = game(20, "B", 1000, 900);
        when(games.findMetadataVerificationRandomSample(any(Pageable.class)))
                .thenReturn(List.of(wrongName, priceChanged));
        when(store.get(10)).thenReturn(Optional.of(detail(10, "B", 1000, 900)));
        when(store.get(20)).thenReturn(Optional.of(detail(20, "B", 2000, 1500)));

        var summary = verifier.verify(2,
                SteamMetadataVerificationService.VerificationMode.RANDOM);

        assertEquals(1, summary.criticalMismatch());
        assertEquals(1, summary.changed());
        assertTrue(summary.results().get(1).mismatchedFields().contains("price_current"));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void unavailableAndHttpErrorAreIsolatedFromOtherMatches(int status) {
        SteamGame a = game(10, "A", 0, 0);
        SteamGame b = game(20, "B", 0, 0);
        SteamGame c = game(30, "C", 0, 0);
        when(games.findMetadataVerificationRecentSample(any(Pageable.class)))
                .thenReturn(List.of(a, b, c));
        when(store.get(10)).thenReturn(Optional.empty());
        when(store.get(20)).thenThrow(HttpStatus.valueOf(status).is4xxClientError()
                ? new org.springframework.web.client.HttpClientErrorException(HttpStatus.valueOf(status))
                : new HttpServerErrorException(HttpStatus.valueOf(status)));
        when(store.get(30)).thenReturn(Optional.of(detail(30, "C", 0, 0)));

        var summary = verifier.verify(3,
                SteamMetadataVerificationService.VerificationMode.RECENT);

        assertEquals(1, summary.storeUnavailable());
        assertEquals(1, summary.verificationError());
        assertEquals(1, summary.matched());
    }

    @Test
    void duplicateCandidateIsRequestedOnlyOnce() {
        SteamGame first = game(570, "Dota 2", 0, 0);
        SteamGame duplicate = game(570, "Wrong duplicate", 0, 0);
        when(games.findMetadataVerificationRandomSample(any(Pageable.class)))
                .thenReturn(List.of(first, duplicate));
        when(store.get(570)).thenReturn(Optional.of(detail(570, "Dota 2", 0, 0)));

        var summary = verifier.verify(100,
                SteamMetadataVerificationService.VerificationMode.RANDOM);

        assertEquals(1, summary.sampled());
        verify(store, times(1)).get(570);
        verify(games, never()).save(any());
    }

    @Test
    void timeoutDoesNotPreventFollowingCandidateVerification() {
        SteamGame timedOut = game(10, "A", 0, 0);
        SteamGame healthy = game(20, "B", 0, 0);
        when(games.findMetadataVerificationRandomSample(any(Pageable.class)))
                .thenReturn(List.of(timedOut, healthy));
        when(store.get(10)).thenThrow(new ResourceAccessException("timed out"));
        when(store.get(20)).thenReturn(Optional.of(detail(20, "B", 0, 0)));

        var summary = verifier.verify(2,
                SteamMetadataVerificationService.VerificationMode.RANDOM);

        assertEquals(1, summary.verificationError());
        assertEquals(1, summary.matched());
        verify(games, never()).save(any());
    }

    private SteamGame game(long appId, String name, Integer original, Integer current) {
        SteamGame game = new SteamGame(appId, name, 0, 0);
        game.updateStoreDetail(name, "game", null, null, false, "KRW", original,
                current, 0, 0, "NON_ADULT", LocalDate.of(2020, 1, 1), "Jan 1, 2020",
                false, false, Set.of("Action"), Set.of("Single-player"),
                true, false, false, false);
        return game;
    }

    private SteamStoreDetailClient.StoreDetail detail(long appId, String name,
            Integer original, Integer current) {
        return new SteamStoreDetailClient.StoreDetail(appId, name, "game", null, null,
                false, "KRW", original, current, 0, 0, "NON_ADULT",
                LocalDate.of(2020, 1, 1), "Jan 1, 2020", false, false,
                Set.of("Action"), Set.of("Single-player"), true, false, false, false);
    }
}
