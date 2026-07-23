package com.mygamehub.eternalreturn;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Component
public class EternalReturnApiClient {

    private final RestClient client;
    private final String apiKey;

    public EternalReturnApiClient(
            RestClient.Builder builder,
            @Value("${app.eternal-return.base-url}") String baseUrl,
            @Value("${app.eternal-return.production-api-key:}") String apiKey
    ) {
        this.client = builder
                .baseUrl(baseUrl)
                .build();

        this.apiKey = apiKey;
    }

    // =========================================================
    // 닉네임 -> UID
    // =========================================================

    public EternalReturnUser getUserByNickname(String nickname) {
        checkApiKey();

        EternalReturnUserResponse response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/user/nickname")
                        .queryParam("query", nickname)
                        .build())
                .header("x-api-key", apiKey)
                .retrieve()
                .body(EternalReturnUserResponse.class);

        if (response == null || response.user() == null) {
            throw new IllegalArgumentException(
                    "이터널 리턴 유저를 찾을 수 없습니다."
            );
        }

        if (response.user().userId() == null ||
                response.user().userId().isBlank()) {

            throw new IllegalArgumentException(
                    "이터널 리턴 UID를 찾을 수 없습니다."
            );
        }

        return response.user();
    }

    // =========================================================
    // 현재 시즌
    // =========================================================

    public int getCurrentSeasonId() {
        checkApiKey();

        EternalReturnSeasonResponse response = client.get()
                .uri("/v2/data/Season")
                .header("x-api-key", apiKey)
                .retrieve()
                .body(EternalReturnSeasonResponse.class);

        if (response == null ||
                response.data() == null ||
                response.data().isEmpty()) {
            throw new IllegalStateException(
                    "이터널 리턴 시즌 정보를 가져오지 못했습니다."
            );
        }

        // 1순위: 공식적으로 현재 시즌이라고 표시된 시즌
        Integer currentSeason = response.data()
                .stream()
                .filter(season ->
                        season.isCurrent() != null &&
                        season.isCurrent() == 1)
                .map(EternalReturnSeason::seasonID)
                .filter(id -> id != null)
                .max(Integer::compareTo)
                .orElse(null);

        if (currentSeason != null) {
            return currentSeason;
        }

        // 혹시 시즌 전환 순간 isCurrent가 비어있는 경우
        // 가장 큰 seasonID를 fallback으로 사용
        return response.data()
                .stream()
                .map(EternalReturnSeason::seasonID)
                .filter(id -> id != null)
                .max(Integer::compareTo)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "이터널 리턴 최신 시즌을 찾을 수 없습니다."
                        )
                );
    }

    // =========================================================
    // 현재 랭크
    // =========================================================

    public EternalReturnRank getRankByUserId(
            String userId,
            int seasonId
    ) {
        checkApiKey();

        EternalReturnRankResponse response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/v1/rank/uid/{userId}/{seasonId}/{matchingTeamMode}"
                        )
                        .build(
                                userId,
                                seasonId,
                                3
                        ))
                .header("x-api-key", apiKey)
                .retrieve()
                .body(EternalReturnRankResponse.class);

        if (response == null || response.userRank() == null) {
            throw new IllegalArgumentException(
                    "이터널 리턴 랭크 정보를 찾을 수 없습니다."
            );
        }

        return response.userRank();
    }

    // =========================================================
    // 랭크 통계
    // =========================================================

    public EternalReturnUserStat getRankedStatsByUserId(
            String userId,
            int seasonId
    ) {
        checkApiKey();

        EternalReturnUserStatsResponse response = client.get()
                .uri(uriBuilder -> uriBuilder
                        .path(
                                "/v2/user/stats/uid/{userId}/{seasonId}/{matchingMode}"
                        )
                        .build(
                                userId,
                                seasonId,
                                3
                        ))
                .header("x-api-key", apiKey)
                .retrieve()
                .body(EternalReturnUserStatsResponse.class);

        if (response == null ||
                response.userStats() == null ||
                response.userStats().isEmpty()) {

            return null;
        }

        return response.userStats().get(0);
    }

    private void checkApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "이터널 리턴 API Key가 설정되지 않았습니다."
            );
        }
    }
    public Map<Integer, String> getCharacterNames(
        List<Integer> characterCodes
) {
    checkApiKey();

    if (characterCodes == null || characterCodes.isEmpty()) {
        return Map.of();
    }

    EternalReturnL10nResponse response = client.get()
            .uri("/v1/l10n/Korean")
            .header("x-api-key", apiKey)
            .retrieve()
            .body(EternalReturnL10nResponse.class);

    if (response == null ||
            response.data() == null ||
            response.data().l10Path() == null ||
            response.data().l10Path().isBlank()) {

        throw new IllegalStateException(
                "이터널 리턴 한국어 l10n 주소를 가져오지 못했습니다."
        );
    }

    String l10nUrl = response.data().l10Path();

    // String 변환 과정의 인코딩 문제를 피하기 위해 byte[]로 직접 받음
    byte[] bytes = RestClient.create()
            .get()
            .uri(l10nUrl)
            .retrieve()
            .body(byte[].class);

    if (bytes == null || bytes.length == 0) {
        throw new IllegalStateException(
                "이터널 리턴 한국어 l10n 파일이 비어 있습니다."
        );
    }

    String text = new String(
            bytes,
            java.nio.charset.StandardCharsets.UTF_8
    );

    Map<Integer, String> result = new HashMap<>();

    for (String line : text.split("\\R")) {
        if (line.isBlank()) {
            continue;
        }

        // 공식 문서의 특수 구분자
        String[] parts = line.split("┃", 2);

        if (parts.length != 2) {
            continue;
        }

        String key = parts[0].trim();
        String value = parts[1].trim();

        if (!key.startsWith("Character/Name/")) {
            continue;
        }

        String codeString =
                key.substring("Character/Name/".length());

        try {
            int code = Integer.parseInt(codeString);

            if (characterCodes.contains(code)) {
                result.put(code, value);
            }
        } catch (NumberFormatException ignored) {
        }
    }

    return result;
}
}