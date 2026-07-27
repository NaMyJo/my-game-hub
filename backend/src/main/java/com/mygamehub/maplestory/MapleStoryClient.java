package com.mygamehub.maplestory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class MapleStoryClient {

    private static final String API_KEY_HEADER = "x-nxopen-api-key";

    private final RestClient restClient;
    private final String apiKey;

    public MapleStoryClient(
            RestClient.Builder builder,
            @Value("${app.maplestory.base-url}") String baseUrl,
            @Value("${app.maplestory.api-key}") String apiKey
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();

        this.apiKey = apiKey;
    }

    public String getOcid(String characterName) {
        validateApiKey();

        try {
            MapleStoryOcidResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/maplestory/v1/id")
                            .queryParam("character_name", characterName)
                            .build())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(API_KEY_HEADER, apiKey)
                    .retrieve()
                    .body(MapleStoryOcidResponse.class);

            if (response == null ||
                    response.ocid() == null ||
                    response.ocid().isBlank()) {
                throw new IllegalArgumentException(
                        "메이플스토리 캐릭터를 찾을 수 없습니다."
                );
            }

            return response.ocid();

        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException(
                    "메이플스토리 캐릭터를 찾을 수 없습니다."
            );
        }
    }

    public MapleStoryBasicResponse getBasicProfile(String ocid) {
        validateApiKey();

        try {
            MapleStoryBasicResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/maplestory/v1/character/basic")
                            .queryParam("ocid", ocid)
                            .build())
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(API_KEY_HEADER, apiKey)
                    .retrieve()
                    .body(MapleStoryBasicResponse.class);

            if (response == null) {
                throw new IllegalArgumentException(
                        "메이플스토리 캐릭터 정보를 불러올 수 없습니다."
                );
            }

            return response;

        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException(
                    "메이플스토리 캐릭터 정보를 찾을 수 없습니다."
            );
        }
    }

    public MapleStoryBasicResponse getProfile(String characterName) {
        String ocid = getOcid(characterName);

        return getBasicProfile(ocid);
    }
    public MapleStoryStatResponse getStat(String ocid) {
    validateApiKey();

    try {
        MapleStoryStatResponse response = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/maplestory/v1/character/stat")
                        .queryParam("ocid", ocid)
                        .build())
                .header(HttpHeaders.ACCEPT, "application/json")
                .header(API_KEY_HEADER, apiKey)
                .retrieve()
                .body(MapleStoryStatResponse.class);

        if (response == null) {
            throw new IllegalArgumentException(
                    "메이플스토리 스탯 정보를 불러올 수 없습니다."
            );
        }

        return response;

    } catch (HttpClientErrorException.NotFound e) {
        throw new IllegalArgumentException(
                "메이플스토리 스탯 정보를 찾을 수 없습니다."
        );
    }
}

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "NEXON_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }
}