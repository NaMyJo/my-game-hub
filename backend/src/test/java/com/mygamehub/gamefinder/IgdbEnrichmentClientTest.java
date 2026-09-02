package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IgdbEnrichmentClientTest {
    @Test
    void mapsTwitchTokenSteamExternalGameAndMultiplayerModes() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Client-ID", "client"))
                .andExpect(header("Authorization", "Bearer token"))
                .andExpect(content().string(containsString("external_game_source = 1")))
                .andExpect(content().string(containsString("uid = (\"570\")")))
                .andRespond(withSuccess("[{\"id\":100,\"game\":42,\"external_game_source\":1,\"uid\":\"570\"}]",
                        MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/multiplayer_modes"))
                .andExpect(content().string(containsString("where game = (42)")))
                .andRespond(withSuccess("""
                        [{"id":7,"game":42,"onlinemax":10,
                        "onlinecoopmax":5,"onlinecoop":true,"offlinemax":2,
                        "offlinecoopmax":2,"offlinecoop":true}]
                        """, MediaType.APPLICATION_JSON));

        var result = context.client().findBySteamAppId(570).orElseThrow();

        assertEquals(42, result.gameId());
        assertEquals(1, result.multiplayerModeCount());
        assertEquals(1, result.minPlayers());
        assertEquals(10, result.maxPlayers());
        assertEquals(10, result.onlineMax());
        assertEquals(5, result.coopMax());
        assertTrue(result.multiplayer());
        assertTrue(result.onlineCoop());
        assertTrue(result.offlineCoop());
        context.server().verify();
    }

    @Test
    void batchesMultipleSteamAppsIntoTwoIgdbDataRequests() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andExpect(content().string(containsString("uid = (\"570\",\"1245620\")")))
                .andRespond(withSuccess("""
                        [{"game":42,"uid":"570"},{"game":119133,"uid":"1245620"}]
                        """, MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/multiplayer_modes"))
                .andExpect(content().string(containsString("where game = (42,119133)")))
                .andExpect(content().string(containsString("limit 500; offset 0")))
                .andRespond(withSuccess("""
                        [{"game":42,"onlinemax":10},{"game":119133,"onlinemax":3,
                          "onlinecoopmax":3,"onlinecoop":true}]
                        """, MediaType.APPLICATION_JSON));

        var result = context.client().findBySteamAppIds(java.util.List.of(570L, 1245620L));

        assertEquals(42, result.get(570L).orElseThrow().gameId());
        assertEquals(119133, result.get(1245620L).orElseThrow().gameId());
        assertEquals(3, result.get(1245620L).orElseThrow().onlineMax());
        context.server().verify();
    }

    @Test
    void mapsReversedExternalAndModeResponsesByIdsAndKeepsNotFoundIsolated() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andRespond(withSuccess("""
                        [{"game":300,"uid":"30"},{"game":100,"uid":"10"}]
                        """, MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/multiplayer_modes"))
                .andRespond(withSuccess("""
                        [{"game":300,"onlinemax":4},{"game":100,"onlinemax":2},
                         {"game":300,"onlinecoopmax":8,"onlinecoop":true},
                         {"game":999,"onlinemax":99}]
                        """, MediaType.APPLICATION_JSON));

        var result = context.client().findBySteamAppIds(java.util.List.of(10L, 20L, 30L));

        assertEquals(100, result.get(10L).orElseThrow().gameId());
        assertEquals(2, result.get(10L).orElseThrow().onlineMax());
        assertTrue(result.get(20L).isEmpty());
        assertEquals(300, result.get(30L).orElseThrow().gameId());
        assertEquals(2, result.get(30L).orElseThrow().multiplayerModeCount());
        assertEquals(8, result.get(30L).orElseThrow().coopMax());
        context.server().verify();
    }

    @Test
    void conflictingDuplicateExternalMappingIsRejectedAndUnrequestedAppIsIgnored() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andRespond(withSuccess("""
                        [{"game":100,"uid":"10"},{"game":100,"uid":"10"},
                         {"game":200,"uid":"10"},{"game":999,"uid":"999"}]
                        """, MediaType.APPLICATION_JSON));

        var result = context.client().findBySteamAppIds(java.util.List.of(10L));

        assertTrue(result.get(10L).isEmpty());
        assertFalse(result.containsKey(999L));
        context.server().verify();
    }

    @Test
    void duplicateIgdbGameIdUsesExplicitExternalLinksWithoutCrossMixing() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andRespond(withSuccess("""
                        [{"game":100,"uid":"10"},{"game":100,"uid":"20"}]
                        """, MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/multiplayer_modes"))
                .andExpect(content().string(containsString("where game = (100)")))
                .andRespond(withSuccess("[{\"game\":100,\"onlinemax\":4}]",
                        MediaType.APPLICATION_JSON));

        var result = context.client().findBySteamAppIds(java.util.List.of(10L, 20L));

        assertEquals(100, result.get(10L).orElseThrow().gameId());
        assertEquals(4, result.get(10L).orElseThrow().onlineMax());
        assertEquals(100, result.get(20L).orElseThrow().gameId());
        assertEquals(4, result.get(20L).orElseThrow().onlineMax());
        context.server().verify();
    }

    @Test
    void mergesPaginatedResponsesByIdsInsteadOfPageOrArrayPosition() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andExpect(content().string(containsString("offset 0")))
                .andRespond(withSuccess(repeatedJson("{\"game\":100,\"uid\":\"10\"}", 500),
                        MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andExpect(content().string(containsString("offset 500")))
                .andRespond(withSuccess("[{\"game\":200,\"uid\":\"20\"}]",
                        MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/multiplayer_modes"))
                .andExpect(content().string(containsString("offset 0")))
                .andRespond(withSuccess(repeatedJson("{\"game\":100,\"onlinemax\":2}", 500),
                        MediaType.APPLICATION_JSON));
        context.server().expect(requestTo("https://api.igdb.com/v4/multiplayer_modes"))
                .andExpect(content().string(containsString("offset 500")))
                .andRespond(withSuccess("[{\"game\":200,\"onlinemax\":7}]",
                        MediaType.APPLICATION_JSON));

        var result = context.client().findBySteamAppIds(java.util.List.of(20L, 10L));

        assertEquals(100, result.get(10L).orElseThrow().gameId());
        assertEquals(2, result.get(10L).orElseThrow().onlineMax());
        assertEquals(200, result.get(20L).orElseThrow().gameId());
        assertEquals(7, result.get(20L).orElseThrow().onlineMax());
        context.server().verify();
    }

    @Test
    void returnsEmptyWhenSteamExternalGameHasNoMatch() {
        TestContext context = context();
        expectToken(context.server());
        expectSteamSource(context.server());
        context.server().expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertTrue(context.client().findBySteamAppId(4436560).isEmpty());
        context.server().verify();
    }

    @Test
    void rejectsInvalidTwitchTokenResponse() {
        TestContext context = context();
        context.server().expect(requestTo(containsString("id.twitch.tv/oauth2/token")))
                .andExpect(queryParam("grant_type", "client_credentials"))
                .andRespond(withSuccess("{\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> context.client().findBySteamAppId(570));
        assertTrue(exception.getMessage().contains("token"));
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    void exposesIgdbHttpFailureWithoutResponseBody(int status) {
        TestContext context = context();
        expectToken(context.server());
        int attempts = status == 429 || status >= 500 ? 3 : 1;
        for (int i = 0; i < attempts; i++) {
            context.server().expect(requestTo("https://api.igdb.com/v4/external_game_sources"))
                    .andRespond(withStatus(HttpStatus.valueOf(status))
                            .body("sensitive upstream response must not be propagated")
                            .contentType(MediaType.TEXT_PLAIN));
        }
        var exception = assertThrows(IgdbEnrichmentClient.IgdbRequestException.class,
                () -> context.client().findBySteamAppId(570));
        assertEquals("external_game_sources", exception.stage());
        assertEquals(status, exception.status());
        assertFalse(exception.getMessage().contains("sensitive"));
    }

    @Test
    void exposesTwitchHttpFailureStatus() {
        TestContext context = context();
        context.server().expect(requestTo(containsString("id.twitch.tv/oauth2/token")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        var exception = assertThrows(IgdbEnrichmentClient.IgdbRequestException.class,
                () -> context.client().findBySteamAppId(570));
        assertEquals("token", exception.stage());
        assertEquals(401, exception.status());
    }

    private static TestContext context() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        return new TestContext(server, new IgdbEnrichmentClient(builder, "client", "secret"));
    }

    private static void expectToken(MockRestServiceServer server) {
        server.expect(requestTo(containsString("id.twitch.tv/oauth2/token")))
                .andExpect(method(HttpMethod.POST))
                .andExpect(queryParam("client_id", "client"))
                .andExpect(queryParam("client_secret", "secret"))
                .andExpect(queryParam("grant_type", "client_credentials"))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":3600,\"token_type\":\"bearer\"}",
                        MediaType.APPLICATION_JSON));
    }

    private static void expectSteamSource(MockRestServiceServer server) {
        server.expect(requestTo("https://api.igdb.com/v4/external_game_sources"))
                .andExpect(content().string(containsString("where name = \"Steam\"")))
                .andRespond(withSuccess("[{\"id\":1,\"name\":\"Steam\"}]", MediaType.APPLICATION_JSON));
    }

    private static String repeatedJson(String value, int count) {
        return "[" + String.join(",", java.util.Collections.nCopies(count, value)) + "]";
    }

    private record TestContext(MockRestServiceServer server, IgdbEnrichmentClient client) {}
}
