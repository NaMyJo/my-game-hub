package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class IgdbEnrichmentClientTest {
    @Test
    void mapsSteamExternalGameAndMultiplayerModes() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("id.twitch.tv/oauth2/token")))
                .andRespond(withSuccess("{\"access_token\":\"token\",\"expires_in\":3600}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.igdb.com/v4/external_games"))
                .andExpect(content().string(containsString("uid = \"570\"")))
                .andRespond(withSuccess("[{\"id\":100,\"game\":42}]",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.igdb.com/v4/games"))
                .andExpect(content().string(containsString("where id = 42")))
                .andRespond(withSuccess("""
                        [{"id":42,"multiplayer_modes":[{"onlinemax":10,
                        "onlinecoopmax":5,"onlinecoop":true,"offlinemax":2,
                        "offlinecoopmax":2,"offlinecoop":true}]}]
                        """, MediaType.APPLICATION_JSON));
        IgdbEnrichmentClient client = new IgdbEnrichmentClient(builder,
                "client", "secret");

        var result = client.findBySteamAppId(570).orElseThrow();

        assertEquals(42, result.gameId());
        assertEquals(1, result.multiplayerModeCount());
        assertEquals(1, result.minPlayers());
        assertEquals(10, result.maxPlayers());
        assertEquals(10, result.onlineMax());
        assertEquals(5, result.coopMax());
        assertTrue(result.multiplayer());
        assertTrue(result.onlineCoop());
        assertTrue(result.offlineCoop());
        server.verify();
    }
}
