package com.mygamehub.lostark;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@Component
public class LostArkClient {

    private final RestClient restClient;
    private final String apiToken;

    public LostArkClient(
            RestClient.Builder builder,
            @Value("${app.lostark.base-url}") String baseUrl,
            @Value("${app.lostark.api-token}") String apiToken
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.apiToken = apiToken;
    }

    public LostArkProfile getProfile(String characterName) {
        if (apiToken == null || apiToken.isBlank()) {
            throw new IllegalStateException(
                    "LOSTARK_API_TOKEN 환경변수가 설정되지 않았습니다."
            );
        }

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/armories/characters/{characterName}/profiles")
                            .build(characterName))
                    .header(HttpHeaders.ACCEPT, "application/json")
                    .header(HttpHeaders.AUTHORIZATION, "bearer " + apiToken)
                    .retrieve()
                    .body(LostArkProfile.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException("로스트아크 캐릭터를 찾을 수 없습니다.");
        }
    }
}
