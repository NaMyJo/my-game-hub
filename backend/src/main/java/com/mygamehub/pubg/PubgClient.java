package com.mygamehub.pubg;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class PubgClient {

    private final RestClient restClient;
    private final String apiKey;

    public PubgClient(
            RestClient.Builder builder,
            @Value("${app.pubg.base-url}") String baseUrl,
            @Value("${app.pubg.api-key}") String apiKey
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();

        this.apiKey = apiKey;
    }

    public PubgPlayerData findPlayer(
            String platform,
            String playerName
    ) {
        validateApiKey();

        try {
            PubgPlayerResponse response =
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/shards/{platform}/players")
                                    .queryParam(
                                            "filter[playerNames]",
                                            playerName
                                    )
                                    .build(platform))
                            .header(
                                    HttpHeaders.AUTHORIZATION,
                                    "Bearer " + apiKey
                            )
                            .header(
                                    HttpHeaders.ACCEPT,
                                    "application/vnd.api+json"
                            )
                            .retrieve()
                            .body(PubgPlayerResponse.class);

            if (response == null ||
                    response.data() == null ||
                    response.data().isEmpty()) {

                throw new IllegalArgumentException(
                        "배틀그라운드 플레이어를 찾을 수 없습니다."
                );
            }

            return response.data().stream()
                    .filter(player ->
                            player.attributes() != null &&
                            player.attributes().name() != null &&
                            player.attributes().name()
                                    .equalsIgnoreCase(playerName)
                    )
                    .findFirst()
                    .orElse(response.data().get(0));

        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException(
                    "배틀그라운드 플레이어를 찾을 수 없습니다."
            );
        }
    }
    public String findCurrentSeasonId(String platform) {
        validateApiKey();

        PubgSeasonResponse response =
                restClient.get()
                        .uri("/shards/{platform}/seasons", platform)
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + apiKey
                        )
                        .header(
                                HttpHeaders.ACCEPT,
                                "application/vnd.api+json"
                        )
                        .retrieve()
                        .body(PubgSeasonResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException(
                    "PUBG 시즌 정보를 불러올 수 없습니다."
            );
        }

        return response.data().stream()
                .filter(season ->
                        season.attributes() != null &&
                        season.attributes().isCurrentSeason() &&
                        !season.attributes().isOffseason()
                )
                .map(PubgSeasonData::id)
                .findFirst()
                .orElseThrow(() ->
                        new IllegalStateException(
                                "현재 PUBG 시즌을 찾을 수 없습니다."
                        )
                );
    }
    public PubgRankedStats getRankedStats(
            String platform,
            String playerId,
            String seasonId
    ) {
        validateApiKey();

        PubgRankedResponse response =
                restClient.get()
                        .uri(
                                "/shards/{platform}/players/{playerId}/seasons/{seasonId}/ranked",
                                platform,
                                playerId,
                                seasonId
                        )
                        .header(
                                HttpHeaders.AUTHORIZATION,
                                "Bearer " + apiKey
                        )
                        .header(
                                HttpHeaders.ACCEPT,
                                "application/vnd.api+json"
                        )
                        .retrieve()
                        .body(PubgRankedResponse.class);

        if (response == null ||
                response.data() == null ||
                response.data().attributes() == null ||
                response.data().attributes().rankedGameModeStats() == null) {

            throw new IllegalArgumentException(
                    "PUBG 랭크 정보를 찾을 수 없습니다."
            );
        }

        var stats =
                response.data()
                        .attributes()
                        .rankedGameModeStats();

        // PC 기준 우선 squad-fpp
        PubgRankedStats result = stats.get("squad-fpp");

        // squad-fpp 기록이 없다면 squad 확인
        if (result == null) {
            result = stats.get("squad");
        }

        if (result == null) {
            throw new IllegalArgumentException(
                    "현재 시즌 PUBG 랭크 기록이 없습니다."
            );
        }

        return result;
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "PUBG_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }
}