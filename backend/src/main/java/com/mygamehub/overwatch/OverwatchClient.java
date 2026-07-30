package com.mygamehub.overwatch;

import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
public class OverwatchClient {

    private static final String SEARCH_URL =
            "https://overwatch.blizzard.com/en-us/search/?q=";

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/131.0.0.0 Safari/537.36";

    private static final int TIMEOUT_MILLIS = 10_000;

    private final OverwatchProfileParser parser;

    public OverwatchClient(
            OverwatchProfileParser parser
    ) {
        this.parser = parser;
    }

    public OverwatchProfile findProfile(
            String battleTag
    ) {
        validateBattleTag(battleTag);

        try {
            String searchUrl =
                    buildSearchUrl(battleTag);

            Document searchDocument =
                    request(searchUrl);

            String careerUrl =
                    parser.parseCareerUrl(searchDocument);

            Document careerDocument =
                    request(careerUrl);


            return parser.parseCareerPage(
                    careerDocument,
                    battleTag.trim(),
                    careerUrl
            );

        } catch (OverwatchProfileUnavailableException e) {
            throw e;

        } catch (OverwatchPageChangedException e) {
            throw e;

        } catch (HttpStatusException e) {
            if (e.getStatusCode() == 404) {
                throw new OverwatchProfileUnavailableException();
            }

            throw new OverwatchPageChangedException(e);

        } catch (IOException e) {
            throw new OverwatchPageChangedException(e);
        }
    }

    private Document request(
            String url
    ) throws IOException {
        Connection.Response response =
                Jsoup.connect(url)
                        .userAgent(USER_AGENT)
                        .header(
                                "Accept",
                                "text/html,application/xhtml+xml,"
                                        + "application/xml;q=0.9,*/*;q=0.8"
                        )
                        .header(
                                "Accept-Language",
                                "en-US,en;q=0.9"
                        )
                        .timeout(TIMEOUT_MILLIS)
                        .followRedirects(true)
                        .ignoreHttpErrors(false)
                        .execute();

        return response.parse();
    }

    private String buildSearchUrl(
            String battleTag
    ) {
        String encodedBattleTag =
                URLEncoder.encode(
                        battleTag.trim(),
                        StandardCharsets.UTF_8
                );

        return SEARCH_URL + encodedBattleTag;
    }

    private void validateBattleTag(
            String battleTag
    ) {
        if (battleTag == null || battleTag.isBlank()) {
            throw new IllegalArgumentException(
                    "BattleTag를 입력해주세요."
            );
        }

        String normalized =
                battleTag.trim();

        int separatorIndex =
                normalized.lastIndexOf('#');

        if (separatorIndex <= 0
                || separatorIndex == normalized.length() - 1) {
            throw new IllegalArgumentException(
                    "BattleTag를 이름#숫자 형식으로 입력해주세요."
            );
        }

        String name =
                normalized.substring(0, separatorIndex).trim();

        String number =
                normalized.substring(separatorIndex + 1).trim();

        if (name.isBlank() || !number.matches("\\d+")) {
            throw new IllegalArgumentException(
                    "BattleTag를 이름#숫자 형식으로 입력해주세요."
            );
        }
    }

  

}