package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SteamCatalogClientTest {
    @Test
    void diagnosticUsesPublicHostInputJsonAndThreeResults() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString(
                        "https://api.steampowered.com/IStoreService/GetAppList/v1/")))
                .andExpect(queryParam("key", "test-key"))
                .andExpect(queryParam("input_json", containsString("%22last_appid%22:0")))
                .andExpect(queryParam("input_json", containsString("%22max_results%22:3")))
                .andExpect(queryParam("input_json", containsString("%22include_games%22:true")))
                .andRespond(withSuccess("""
                        {"response":{"apps":[{"appid":10,"name":"A","last_modified":100,
                        "price_change_number":1}],"have_more_results":true,"last_appid":10}}
                        """, MediaType.APPLICATION_JSON));
        SteamCatalogClient client = new SteamCatalogClient(builder, "test-key", 5000,
                "https://api.steampowered.com", new ExternalApiRetry(ms -> {}));

        SteamCatalogClient.CatalogPage page = client.diagnose();

        assertEquals(1, page.items().size());
        assertTrue(page.hasMore());
        assertEquals(10, page.lastAppId());
        server.verify();
    }

    @Test
    void forbiddenReportsStatusWithoutExposingKey() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(containsString("IStoreService/GetAppList/v1/")))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("Forbidden"));
        String key = "must-not-appear";
        SteamCatalogClient client = new SteamCatalogClient(builder, key, 5000,
                "https://partner.steam-api.com", new ExternalApiRetry(ms -> {}));

        SteamCatalogClient.SteamCatalogHttpException exception = assertThrows(
                SteamCatalogClient.SteamCatalogHttpException.class, client::diagnose);

        assertEquals(403, exception.status());
        assertFalse(exception.getMessage().contains(key));
        assertFalse(exception.getMessage().contains("Forbidden"));
        server.verify();
    }

    @Test
    void missingKeyFailsBeforeHttpRequest() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        SteamCatalogClient client = new SteamCatalogClient(builder, "", 5000,
                "https://api.steampowered.com", new ExternalApiRetry(ms -> {}));

        assertThrows(IllegalStateException.class, client::diagnose);
        server.verify();
    }
}
