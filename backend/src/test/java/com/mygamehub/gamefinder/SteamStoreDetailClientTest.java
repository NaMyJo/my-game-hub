package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import java.util.concurrent.atomic.AtomicLong;

class SteamStoreDetailClientTest {
    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void retriesTransientHttpFailureAndStopsAfterThreeAttempts(int status) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String url = "https://store.steampowered.com/api/appdetails?appids=570&cc=kr&l=korean";
        for (int i = 0; i < 3; i++) server.expect(requestTo(url)).andRespond(withStatus(HttpStatus.valueOf(status)));
        SteamStoreDetailClient client = new SteamStoreDetailClient(builder, policy(2));
        assertThrows(RuntimeException.class, () -> client.get(570));
        server.verify();
    }

    @Test
    void parsesUpcomingGameAndRequiredAgeFromStoreResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://store.steampowered.com/api/appdetails?appids=4436560&cc=kr&l=korean"))
                .andRespond(withSuccess("""
                        {"4436560":{"success":true,"data":{"type":"game",
                        "name":"We Need More Humans!","steam_appid":4436560,
                        "required_age":18,"is_free":false,
                        "header_image":"https://example.test/header.jpg",
                        "price_overview":{"currency":"KRW","initial":2100000,
                        "final":1890000,"discount_percent":10},
                        "categories":[{"id":2,"description":"싱글 플레이어"}],
                        "genres":[{"id":"1","description":"액션"}],
                        "release_date":{"coming_soon":true,"date":"2026년 9월 1일"}}}}
                        """, MediaType.APPLICATION_JSON));
        SteamStoreDetailClient client = new SteamStoreDetailClient(builder,
                policy(2));

        var detail = client.get(4436560).orElseThrow();

        assertEquals(4436560, detail.steamAppId());
        assertEquals("We Need More Humans!", detail.name());
        assertEquals("game", detail.type());
        assertEquals("KRW", detail.currency());
        assertEquals(21_000, detail.original());
        assertEquals(18_900, detail.current());
        assertEquals(10, detail.discount());
        assertEquals(18, detail.requiredAge());
        assertEquals("ADULT", detail.adult());
        assertTrue(detail.comingSoon());
        assertEquals("2026년 9월 1일", detail.releaseText());
        assertEquals(java.time.LocalDate.of(2026, 9, 1), detail.releaseDate());
        server.verify();
    }

    @Test
    void freeGameDoesNotRequirePriceOverview() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://store.steampowered.com/api/appdetails?appids=570&cc=kr&l=korean"))
                .andRespond(withSuccess("""
                        {"570":{"success":true,"data":{"type":"game","name":"Dota 2",
                        "steam_appid":570,"required_age":0,"is_free":true,
                        "categories":[],"genres":[],
                        "release_date":{"coming_soon":false,"date":"2013년 7월 9일"}}}}
                        """, MediaType.APPLICATION_JSON));
        SteamStoreDetailClient client = new SteamStoreDetailClient(builder,
                policy(2));

        var detail = client.get(570).orElseThrow();

        assertTrue(detail.free());
        assertEquals(0, detail.original());
        assertEquals(0, detail.current());
        assertNull(detail.currency());
        server.verify();
    }

    @Test
    void upcomingPaidGameMayHaveUnknownPrice() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://store.steampowered.com/api/appdetails?appids=4436560&cc=kr&l=korean"))
                .andRespond(withSuccess("""
                        {"4436560":{"success":true,"data":{"type":"game",
                        "name":"We Need More Humans!","steam_appid":4436560,
                        "required_age":0,"is_free":false,"categories":[],"genres":[],
                        "release_date":{"coming_soon":true,"date":"2026년 9월 1일"}}}}
                        """, MediaType.APPLICATION_JSON));
        SteamStoreDetailClient client = new SteamStoreDetailClient(builder,
                policy(2));

        var detail = client.get(4436560).orElseThrow();

        assertFalse(detail.free());
        assertNull(detail.original());
        assertNull(detail.current());
        assertTrue(detail.comingSoon());
        server.verify();
    }

    @Test
    void rejectsResponseWhoseDataAppIdDiffersFromRequestedApp() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://store.steampowered.com/api/appdetails?appids=570&cc=kr&l=korean"))
                .andRespond(withSuccess("""
                        {"570":{"success":true,"data":{"type":"game","name":"Wrong",
                        "steam_appid":1245620,"required_age":0,"is_free":true,
                        "categories":[],"genres":[],
                        "release_date":{"coming_soon":false,"date":""}}}}
                        """, MediaType.APPLICATION_JSON));
        SteamStoreDetailClient client = new SteamStoreDetailClient(builder, policy(0));

        assertThrows(SteamStoreDetailClient.SteamStoreResponseException.class,
                () -> client.get(570));
        server.verify();
    }

    private SteamStoreRequestPolicy policy(int maxRetries) {
        AtomicLong nanoTime = new AtomicLong();
        return new SteamStoreRequestPolicy(0, maxRetries, 0, 1, 2, 1,
                millis -> nanoTime.addAndGet(millis * 1_000_000L), nanoTime::get);
    }
}
