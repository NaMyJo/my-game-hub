package com.mygamehub.valorant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import com.fasterxml.jackson.databind.ObjectMapper;


@Component
public class ValorantClient {

    private static final String DEFAULT_REGION = "kr";
    private static final String DEFAULT_PLATFORM = "pc";
    private final RestClient restClient;

    public ValorantClient(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${app.henrik.api.base-url}") String baseUrl,
            @Value("${app.henrik.api.key}") String apiKey
    ) {
        this.restClient = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", apiKey)
                .build();

    }

        public ValorantProfile getProfile(String riotId) {


            RiotId parsedRiotId = parseRiotId(riotId);


            ValorantAccountResponse accountResponse =
                    getAccount(
                            parsedRiotId.name(),
                            parsedRiotId.tag()
                    );

        if (accountResponse == null ||
            accountResponse.data() == null) {

            return new ValorantProfile(
                    parsedRiotId.name(),
                    parsedRiotId.tag(),
                    "kr",
                    "데이터 없음",
                    null,
                    null,
                    null
            );
        }

            ValorantAccountResponse.Data accountData =
                    accountResponse.data();

            String region =
                    normalizeRegion(accountData.region());

            ValorantMmrResponse mmrResponse =
                    getMmr(
                            region,
                            parsedRiotId.name(),
                            parsedRiotId.tag()
                    );
                

            // 현재 시즌 경쟁전 기록이 없을 때 기본값
            String tier = "경쟁전 미진행";
            Integer rr = null;

            if (mmrResponse != null &&
                    mmrResponse.data() != null &&
                    mmrResponse.data().current() != null) {

                ValorantMmrResponse.Current current =
                        mmrResponse.data().current();

                String tierName =
                        current.tier() == null
                                ? null
                                : current.tier().name();

                boolean hasRank =
                        tierName != null &&
                        !tierName.isBlank() &&
                        !tierName.equalsIgnoreCase("Unrated");

                if (hasRank) {
                    tier = tierName;
                    rr = current.rr();
                }
            }

            String cardImageUrl = null;

            if (accountData.card() != null) {
                cardImageUrl = accountData.card().large();
            }

            // 반드시 메서드 마지막에 반환
            return new ValorantProfile(
                    accountData.name(),
                    accountData.tag(),
                    region,
                    tier,
                    rr,
                    accountData.accountLevel(),
                    cardImageUrl
            );
        }
    private ValorantAccountResponse getAccount(
            String name,
            String tag
    ) {

        try {

            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/valorant/v1/account/{name}/{tag}")
                            .build(name, tag)
                    )
                    .retrieve()
                    .body(ValorantAccountResponse.class);

        } catch (HttpClientErrorException.NotFound e) {

            String body = e.getResponseBodyAsString();

            System.out.println(body);

            // Henrik code 24
            if (body.contains("\"code\":24")) {
                return null;
            }

            throw new IllegalArgumentException("존재하지 않는 Riot ID입니다.");
        }
    }

    private ValorantMmrResponse getMmr(
            String region,
            String name,
            String tag
    ) {
        try {

            ValorantMmrResponse response =
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/valorant/v3/mmr/{region}/{platform}/{name}/{tag}")
                                    .build(
                                            region,
                                            DEFAULT_PLATFORM,
                                            name,
                                            tag
                                    )
                            )
                            .retrieve()
                            .body(ValorantMmrResponse.class);

    
            return response;

        } catch (HttpClientErrorException e) {

          
            if (e.getStatusCode().value() == 404) {
                return null;
            }

            throw e;
        }
    }

    private RiotId parseRiotId(String accountName) {
        if (accountName == null || accountName.isBlank()) {
            throw new IllegalArgumentException(
                    "발로란트 Riot ID를 입력해주세요."
            );
        }

        String[] parts =
                accountName.trim().split("#", 2);

        if (parts.length != 2 ||
                parts[0].isBlank() ||
                parts[1].isBlank()) {
            throw new IllegalArgumentException(
                    "Riot ID는 게임이름#태그 형식으로 입력해주세요."
            );
        }

        return new RiotId(
                parts[0].trim(),
                parts[1].trim()
        );
    }

    private String normalizeRegion(String region) {
        if (region == null || region.isBlank()) {
            return DEFAULT_REGION;
        }

        String normalized =
                region.trim().toLowerCase();

        return switch (normalized) {
            case "kr", "ap", "eu", "na",
                 "latam", "br" -> normalized;

            default -> DEFAULT_REGION;
        };
    }

    private record RiotId(
            String name,
            String tag
    ) {
    }
}