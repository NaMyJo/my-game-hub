package com.mygamehub.riot;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;

@Component
public class RiotApiClient {

    private final RestClient accountClient;
    private final RestClient krClient;

    private final String devApiKey;
    private final String lolApiKey;
    private final String tftApiKey;

    public RiotApiClient(
            RestClient.Builder builder,

            @Value("${app.riot.dev-api-key:}")
            String devApiKey,

            @Value("${app.riot.lol-api-key:}")
            String lolApiKey,

            @Value("${app.riot.tft-api-key:}")
            String tftApiKey
    ) {

        this.accountClient = builder
                .baseUrl("https://asia.api.riotgames.com")
                .build();

        this.krClient = builder
                .baseUrl("https://kr.api.riotgames.com")
                .build();

        this.devApiKey = devApiKey;
        this.lolApiKey = lolApiKey;
        this.tftApiKey = tftApiKey;
    }

    // =========================================================
    // API KEY 선택
    // =========================================================

    private String getLolApiKey() {

        if (lolApiKey != null && !lolApiKey.isBlank()) {
            return lolApiKey;
        }

        return getDevApiKey();
    }

    private String getTftApiKey() {

        if (tftApiKey != null && !tftApiKey.isBlank()) {
            return tftApiKey;
        }

        return getDevApiKey();
    }

    private String getDevApiKey() {

        if (devApiKey == null || devApiKey.isBlank()) {
            throw new IllegalStateException(
                    "사용 가능한 Riot API Key가 없습니다."
            );
        }

        return devApiKey;
    }

    // =========================================================
    // Riot Account
    // =========================================================

    private RiotAccount getAccount(
            String gameName,
            String tagLine,
            String apiKey
    ) {

        return accountClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/riot/account/v1/accounts/by-riot-id/{gameName}/{tagLine}"
                        )
                        .build(gameName, tagLine)
                )
                .header(
                        "X-Riot-Token",
                        apiKey
                )
                .header(
                        HttpHeaders.ACCEPT,
                        "application/json"
                )
                .retrieve()
                .body(RiotAccount.class);
    }

    public RiotAccount getLolAccount(
            String gameName,
            String tagLine
    ) {

        return getAccount(
                gameName,
                tagLine,
                getLolApiKey()
        );
    }

    public RiotAccount getTftAccount(
            String gameName,
            String tagLine
    ) {

        return getAccount(
                gameName,
                tagLine,
                getTftApiKey()
        );
    }

    // =========================================================
    // League of Legends
    // =========================================================

    public List<RiotLeagueEntry> getLeagueEntries(
            String puuid
    ) {

        RiotLeagueEntry[] result = krClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/lol/league/v4/entries/by-puuid/{puuid}"
                        )
                        .build(puuid)
                )
                .header(
                        "X-Riot-Token",
                        getLolApiKey()
                )
                .header(
                        HttpHeaders.ACCEPT,
                        "application/json"
                )
                .retrieve()
                .body(RiotLeagueEntry[].class);

        return result == null
                ? List.of()
                : Arrays.asList(result);
    }

    // =========================================================
    // TFT
    // =========================================================

    public List<TftLeagueEntry> getTftLeagueEntries(
            String puuid
    ) {

        TftLeagueEntry[] result = krClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/tft/league/v1/by-puuid/{puuid}"
                        )
                        .build(puuid)
                )
                .header(
                        "X-Riot-Token",
                        getTftApiKey()
                )
                .header(
                        HttpHeaders.ACCEPT,
                        "application/json"
                )
                .retrieve()
                .body(TftLeagueEntry[].class);

        return result == null
                ? List.of()
                : Arrays.asList(result);
    }
}