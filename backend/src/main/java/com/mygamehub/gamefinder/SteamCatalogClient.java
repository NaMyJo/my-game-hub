package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class SteamCatalogClient {
    private static final Logger log = LoggerFactory.getLogger(SteamCatalogClient.class);
    private static final String PATH = "/IStoreService/GetAppList/v1/";

    private final RestClient client;
    private final String apiKey;
    private final int pageSize;
    private final ExternalApiRetry retry;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    @Autowired
    public SteamCatalogClient(RestClient.Builder builder,
            @Value("${app.game-finder.steam-api-key:}") String apiKey,
            @Value("${app.game-finder.catalog-page-size:5000}") int pageSize,
            @Value("${app.game-finder.steam-catalog-base-url:https://api.steampowered.com}") String baseUrl,
            ExternalApiRetry retry, ObjectMapper objectMapper) {
        this.client = builder.baseUrl(baseUrl).build();
        this.apiKey = apiKey;
        this.pageSize = Math.min(Math.max(1, pageSize), 50000);
        this.baseUrl = baseUrl;
        this.retry = retry;
        this.objectMapper = objectMapper;
        log.info("steam_catalog_config apiKeyPresent={} host={} pageSize={}",
                !apiKey.isBlank(), safeHost(baseUrl), this.pageSize);
    }

    SteamCatalogClient(RestClient.Builder builder, String apiKey, int pageSize,
            String baseUrl, ExternalApiRetry retry) {
        this(builder, apiKey, pageSize, baseUrl, retry, new ObjectMapper());
    }

    public CatalogPage page(long lastAppId, Long modifiedSince) {
        return requestPage(lastAppId, modifiedSince, pageSize);
    }

    public CatalogPage diagnose() {
        CatalogPage page = requestPage(0, null, 3);
        log.info("steam_catalog_diagnostic_success host={} requestedMaxResults=3 returnedCount={} "
                        + "hasMore={} lastAppId={}",
                safeHost(baseUrl), page.items().size(), page.hasMore(), page.lastAppId());
        return page;
    }

    private CatalogPage requestPage(long lastAppId, Long modifiedSince, int maxResults) {
        if (apiKey.isBlank()) {
            log.warn("steam_catalog_config apiKeyPresent=false host={}", safeHost(baseUrl));
            throw new IllegalStateException("STEAM_WEB_API_KEY 환경변수가 설정되지 않았습니다.");
        }
        String inputJson = inputJson(lastAppId, modifiedSince, maxResults);
        JsonNode root;
        try {
            root = retry.execute(() -> requestOnce(inputJson));
        } catch (SteamCatalogHttpException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // Do not propagate the original exception: its request URI contains the API key.
            log.warn("steam_catalog_transport_error host={} errorType={}",
                    safeHost(baseUrl), exception.getClass().getSimpleName());
            throw new IllegalStateException("Steam catalog 요청 전송에 실패했습니다. errorType="
                    + exception.getClass().getSimpleName());
        }
        JsonNode response = root == null ? null : root.path("response");
        List<CatalogItem> items = new ArrayList<>();
        if (response != null) {
            for (JsonNode app : response.path("apps")) {
                items.add(new CatalogItem(app.path("appid").asLong(), app.path("name").asText(),
                        app.path("last_modified").asLong(), app.path("price_change_number").asLong()));
            }
        }
        return new CatalogPage(items,
                response != null && response.path("have_more_results").asBoolean(false),
                response == null ? lastAppId : response.path("last_appid").asLong(lastAppId));
    }

    private JsonNode requestOnce(String inputJson) {
        URI uri = UriComponentsBuilder.fromPath(PATH)
                .queryParam("key", apiKey)
                .queryParam("input_json", inputJson)
                .build()
                .encode(StandardCharsets.UTF_8)
                .toUri();
        return client.get().uri(uri)
                .retrieve()
                .onStatus(status -> status.isError(), (request, response) -> {
                    int status = response.getStatusCode().value();
                    log.warn("steam_catalog_http_error host={} status={} apiKeyPresent=true",
                            safeHost(baseUrl), status);
                    throw new SteamCatalogHttpException(status,
                            ExternalApiRetry.parseRetryAfter(response.getHeaders().getFirst("Retry-After")));
                })
                .body(JsonNode.class);
    }

    private String inputJson(long lastAppId, Long modifiedSince, int maxResults) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("include_games", true);
        input.put("include_dlc", false);
        input.put("include_software", false);
        input.put("include_videos", false);
        input.put("include_hardware", false);
        input.put("last_appid", lastAppId);
        input.put("max_results", maxResults);
        if (modifiedSince != null && modifiedSince > 0) input.put("if_modified_since", modifiedSince);
        try {
            return objectMapper.writeValueAsString(input);
        } catch (Exception exception) {
            throw new IllegalStateException("Steam catalog input_json 생성에 실패했습니다.");
        }
    }

    private static String safeHost(String baseUrl) {
        try { return java.net.URI.create(baseUrl).getHost(); }
        catch (Exception ignored) { return "invalid-host"; }
    }

    public int pageSize() { return pageSize; }

    static final class SteamCatalogHttpException extends RuntimeException
            implements ExternalApiRetry.RetryableFailure {
        private final int status;
        private final Long retryAfterMillis;
        SteamCatalogHttpException(int status, Long retryAfterMillis) {
            super("Steam catalog HTTP 요청 실패: status=" + status);
            this.status = status;
            this.retryAfterMillis = retryAfterMillis;
        }
        int status() { return status; }
        @Override public boolean isRetryable() { return status == 429 || status >= 500; }
        @Override public Long retryAfterMillis() { return retryAfterMillis; }
    }

    public record CatalogItem(long appId, String name, long lastModified, long priceChangeNumber) {}
    public record CatalogPage(List<CatalogItem> items, boolean hasMore, long lastAppId) {}
}
