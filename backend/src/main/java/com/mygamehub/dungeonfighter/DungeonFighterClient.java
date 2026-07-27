package com.mygamehub.dungeonfighter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class DungeonFighterClient {

    private final RestClient restClient;
    private final String apiKey;

    public DungeonFighterClient(
            RestClient.Builder builder,
            @Value("${app.neople.base-url}") String baseUrl,
            @Value("${app.neople.api-key}") String apiKey
    ) {
        this.restClient = builder
                .baseUrl(baseUrl)
                .build();

        this.apiKey = apiKey;
    }

    public DungeonFighterCharacter findCharacter(
            String serverId,
            String characterName
    ) {
        validateApiKey();

        try {
            DungeonFighterCharacterSearchResponse response =
                    restClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/df/servers/{serverId}/characters")
                                    .queryParam(
                                            "characterName",
                                            characterName
                                    )
                                    .queryParam(
                                            "wordType",
                                            "match"
                                    )
                                    .queryParam(
                                            "limit",
                                            10
                                    )
                                    .queryParam(
                                            "apikey",
                                            apiKey
                                    )
                                    .build(serverId))
                            .retrieve()
                            .body(
                                    DungeonFighterCharacterSearchResponse.class
                            );

            if (response == null ||
                    response.rows() == null ||
                    response.rows().isEmpty()) {

                throw new IllegalArgumentException(
                        "던전앤파이터 캐릭터를 찾을 수 없습니다."
                );
            }

            return response.rows()
                    .stream()
                    .filter(character ->
                            character.characterName()
                                    .equalsIgnoreCase(characterName))
                    .findFirst()
                    .orElseThrow(() ->
                            new IllegalArgumentException(
                                    "던전앤파이터 캐릭터를 찾을 수 없습니다."
                            )
                    );

        } catch (HttpClientErrorException.NotFound e) {
            throw new IllegalArgumentException(
                    "던전앤파이터 캐릭터를 찾을 수 없습니다."
            );
        }
    }

    private void validateApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "NEOPLE_API_KEY 환경변수가 설정되지 않았습니다."
            );
        }
    }
}