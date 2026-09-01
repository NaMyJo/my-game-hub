package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.Optional;

@Component
public class IgdbEnrichmentClient {
    private static final Logger log = LoggerFactory.getLogger(IgdbEnrichmentClient.class);
    private static final String IGDB_API = "https://api.igdb.com/v4";
    private final RestClient rest;
    private final String clientId;
    private final String secret;
    private final ExternalApiRetry retry;
    private final long requestIntervalMs;
    private long lastRequestNanos;
    private String token;
    private Instant tokenExpiresAt = Instant.EPOCH;
    private Long steamSourceId;

    @Autowired
    public IgdbEnrichmentClient(RestClient.Builder builder,
            @Value("${app.game-finder.igdb-client-id:}") String clientId,
            @Value("${app.game-finder.igdb-client-secret:}") String secret,
            ExternalApiRetry retry,
            @Value("${app.game-finder.igdb-request-interval-ms:260}") long requestIntervalMs) {
        this.rest = builder.build();
        this.clientId = clientId;
        this.secret = secret;
        this.retry = retry;
        this.requestIntervalMs = Math.max(250, requestIntervalMs);
        log.info("igdb_configuration configured={} clientIdPresent={} clientSecretPresent={}",
                configured(), !clientId.isBlank(), !secret.isBlank());
    }

    IgdbEnrichmentClient(RestClient.Builder builder, String clientId, String secret) {
        this.rest = builder.build();
        this.clientId = clientId;
        this.secret = secret;
        this.retry = new ExternalApiRetry(millis -> {});
        this.requestIntervalMs = 0;
    }

    public boolean configured() {
        return !clientId.isBlank() && !secret.isBlank();
    }

    public Optional<IgdbData> findBySteamAppId(long appId) {
        if (!configured()) {
            log.info("igdb_enrichment_skipped appId={} reason=not_configured", appId);
            return Optional.empty();
        }
        String bearer = token();
        long sourceId = steamSourceId(bearer);
        JsonNode links = post("external_games",
                "fields game,external_game_source,uid; where external_game_source = " + sourceId
                        + " & uid = \"" + appId + "\"; limit 10;",
                bearer, appId);
        int linkCount = arraySize(links);
        log.info("igdb_external_games_result appId={} matchCount={}", appId, linkCount);
        if (linkCount == 0) return Optional.empty();

        long gameId = links.get(0).path("game").asLong(0);
        if (gameId <= 0) {
            log.warn("igdb_external_games_invalid appId={} reason=missing_game_id", appId);
            return Optional.empty();
        }
        JsonNode games = post("games", "fields id,name; where id = " + gameId + "; limit 1;", bearer, appId);
        int gameCount = arraySize(games);
        log.info("igdb_games_result appId={} igdbGameId={} matchCount={}", appId, gameId, gameCount);
        if (gameCount == 0) return Optional.empty();

        JsonNode modes = post("multiplayer_modes",
                "fields campaigncoop,dropin,lancoop,offlinecoop,offlinecoopmax,offlinemax,"
                        + "onlinecoop,onlinecoopmax,onlinemax,platform,splitscreen,splitscreenonline; "
                        + "where game = " + gameId + "; limit 50;",
                bearer, appId);
        int modeCount = arraySize(modes);
        log.info("igdb_multiplayer_modes_result appId={} igdbGameId={} matchCount={}", appId, gameId, modeCount);

        int min = Integer.MAX_VALUE, max = 0, onlineMax = 0, coopMax = 0;
        boolean onlineCoop = false, offlineCoop = false;
        if (modes != null && modes.isArray()) {
            for (JsonNode mode : modes) {
                int online = mode.path("onlinemax").asInt(0);
                int offline = mode.path("offlinemax").asInt(0);
                int onlineCoopCount = mode.path("onlinecoopmax").asInt(0);
                int offlineCoopCount = mode.path("offlinecoopmax").asInt(0);
                onlineMax = Math.max(onlineMax, online);
                coopMax = Math.max(coopMax, Math.max(onlineCoopCount, offlineCoopCount));
                max = Math.max(max, Math.max(online, Math.max(offline, Math.max(onlineCoopCount, offlineCoopCount))));
                onlineCoop |= onlineCoopCount > 0 || mode.path("onlinecoop").asBoolean(false);
                offlineCoop |= offlineCoopCount > 0 || mode.path("offlinecoop").asBoolean(false);
                if (max > 0) min = 1;
            }
        }
        return Optional.of(new IgdbData(gameId, modeCount, min == Integer.MAX_VALUE ? null : min,
                max == 0 ? null : max, onlineMax == 0 ? null : onlineMax, coopMax == 0 ? null : coopMax,
                modeCount > 0, onlineCoop, offlineCoop));
    }

    private synchronized long steamSourceId(String bearer) {
        if (steamSourceId != null) return steamSourceId;
        JsonNode sources = post("external_game_sources", "fields name; where name = \"Steam\"; limit 2;",
                bearer, null);
        int matchCount = arraySize(sources);
        log.info("igdb_external_game_source_result source=Steam matchCount={}", matchCount);
        if (matchCount == 0 || sources.get(0).path("id").asLong(0) <= 0) {
            throw new IllegalStateException("IGDB Steam external game source를 찾지 못했습니다.");
        }
        steamSourceId = sources.get(0).path("id").asLong();
        return steamSourceId;
    }

    private JsonNode post(String endpoint, String body, String bearer, Long appId) {
        pace();
        return retry.execute(() -> postOnce(endpoint, body, bearer, appId));
    }

    private JsonNode postOnce(String endpoint, String body, String bearer, Long appId) {
        try {
            return rest.post().uri(IGDB_API + "/" + endpoint).contentType(MediaType.TEXT_PLAIN)
                    .header("Client-ID", clientId).header("Authorization", "Bearer " + bearer).body(body)
                    .retrieve().onStatus(status -> status.isError(), (request, response) -> {
                        int status = response.getStatusCode().value();
                        log.warn("igdb_http_error stage={} appId={} status={}", endpoint, appId, status);
                        throw new IgdbRequestException(endpoint, status,
                                ExternalApiRetry.parseRetryAfter(response.getHeaders().getFirst("Retry-After")));
                    }).body(JsonNode.class);
        } catch (IgdbRequestException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("igdb_transport_error stage={} appId={} exceptionType={}",
                    endpoint, appId, exception.getClass().getSimpleName());
            throw new IllegalStateException("IGDB 요청 전송에 실패했습니다. stage=" + endpoint);
        }
    }

    private synchronized void pace() {
        if (requestIntervalMs <= 0) return;
        long elapsedMs = (System.nanoTime() - lastRequestNanos) / 1_000_000L;
        long waitMs = lastRequestNanos == 0 ? 0 : requestIntervalMs - elapsedMs;
        if (waitMs > 0) {
            try { Thread.sleep(waitMs); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException("IGDB 요청 대기가 중단되었습니다.", e); }
        }
        lastRequestNanos = System.nanoTime();
    }

    private synchronized String token() {
        if (token != null && Instant.now().isBefore(tokenExpiresAt)) return token;
        JsonNode response;
        try {
            response = rest.post().uri(builder -> builder.scheme("https").host("id.twitch.tv")
                    .path("/oauth2/token").queryParam("client_id", clientId).queryParam("client_secret", secret)
                    .queryParam("grant_type", "client_credentials").build()).retrieve()
                    .onStatus(status -> status.isError(), (request, httpResponse) -> {
                        int status = httpResponse.getStatusCode().value();
                        log.warn("igdb_token_http_error status={}", status);
                        throw new IgdbRequestException("token", status,
                                ExternalApiRetry.parseRetryAfter(httpResponse.getHeaders().getFirst("Retry-After")));
                    }).body(JsonNode.class);
        } catch (IgdbRequestException exception) {
            throw exception;
        } catch (RestClientException exception) {
            // The original exception may contain the OAuth URI, including client_secret.
            log.warn("igdb_token_transport_error exceptionType={}", exception.getClass().getSimpleName());
            throw new IllegalStateException("IGDB access token 요청 전송에 실패했습니다.");
        }
        String accessToken = response == null ? "" : response.path("access_token").asText("");
        long expiresIn = response == null ? 0 : response.path("expires_in").asLong(0);
        if (accessToken.isBlank() || expiresIn <= 0) {
            log.warn("igdb_token_invalid accessTokenPresent={} expiresIn={}", !accessToken.isBlank(), expiresIn);
            throw new IllegalStateException("IGDB access token 응답이 올바르지 않습니다.");
        }
        token = accessToken;
        tokenExpiresAt = Instant.now().plusSeconds(Math.max(1, expiresIn - 60));
        log.info("igdb_token_received expiresInSeconds={}", expiresIn);
        return token;
    }

    private static int arraySize(JsonNode node) {
        return node != null && node.isArray() ? node.size() : 0;
    }

    static final class IgdbRequestException extends RuntimeException implements ExternalApiRetry.RetryableFailure {
        private final String stage;
        private final int status;
        private final Long retryAfterMillis;
        IgdbRequestException(String stage, int status, Long retryAfterMillis) {
            super("IGDB request failed: stage=" + stage + ", status=" + status);
            this.stage = stage;
            this.status = status;
            this.retryAfterMillis = retryAfterMillis;
        }
        String stage() { return stage; }
        int status() { return status; }
        @Override public boolean isRetryable() { return status == 429 || status >= 500; }
        @Override public Long retryAfterMillis() { return retryAfterMillis; }
    }

    public record IgdbData(Long gameId, Integer multiplayerModeCount, Integer minPlayers,
            Integer maxPlayers, Integer onlineMax, Integer coopMax, Boolean multiplayer,
            Boolean onlineCoop, Boolean offlineCoop) {}
}
