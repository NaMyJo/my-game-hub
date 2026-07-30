package com.mygamehub.overwatch;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

@Component
public class OverwatchProfileParser {

    private static final String OVERWATCH_BASE_URL =
            "https://overwatch.blizzard.com";

    public String parseCareerUrl(Document searchDocument) {

        if (searchDocument == null) {
            throw new OverwatchPageChangedException();
        }

        String pageText = searchDocument.text();

        if (isNoProfilePage(pageText)) {
            throw new OverwatchProfileUnavailableException();
        }

        Element careerLink =
                searchDocument.selectFirst("a[href*='/career/']");

        if (careerLink == null) {
            throw new OverwatchPageChangedException();
        }

        String href = careerLink.attr("href");

        if (href == null || href.isBlank()) {
            throw new OverwatchPageChangedException();
        }

        return toAbsoluteUrl(href);
    }

    public OverwatchProfile parseCareerPage(
            Document careerDocument,
            String battleTag,
            String careerUrl
    ) {
        if (careerDocument == null) {
            throw new OverwatchPageChangedException();
        }

        String pageText = careerDocument.text();

        boolean hasCareerStructure =
                pageText.contains("Career Stats")
                        || pageText.contains("Competitive")
                        || pageText.contains("Quick Play");

        if (!hasCareerStructure) {
            throw new OverwatchPageChangedException();
        }

        printCareerStructure(careerDocument);

        return new OverwatchProfile(
                battleTag,
                careerUrl,
                null,
                null,
                null
        );
    }

    private void printCareerStructure(Document document) {

        System.out.println(
                "========== OVERWATCH CAREER DEBUG START =========="
        );

        System.out.println(
                "페이지 제목: " + document.title()
        );

        Elements headings =
                document.select("h1, h2, h3, h4, h5, h6");

        System.out.println("----- HEADINGS -----");

        for (Element heading : headings) {
            String text = heading.text().trim();

            if (!text.isBlank()) {
                System.out.println(
                        heading.tagName()
                                + " | class="
                                + heading.className()
                                + " | text="
                                + text
                );
            }
        }

        System.out.println("----- COMPETITIVE ELEMENTS -----");

        Elements competitiveElements =
                document.getElementsContainingOwnText(
                        "Competitive"
                );

        for (Element element : competitiveElements) {
            printElementWithParents(element);
        }

        System.out.println("----- ROLE ELEMENTS -----");

        printElementsContaining(document, "Tank");
        printElementsContaining(document, "Damage");
        printElementsContaining(document, "Support");

        System.out.println("----- RANK IMAGE ALT/TITLE -----");

        Elements rankImages =
                document.select(
                        "img[alt], img[title], "
                                + "[aria-label], [data-tooltip]"
                );

        for (Element element : rankImages) {
            String searchable =
                    (
                            element.attr("alt")
                                    + " "
                                    + element.attr("title")
                                    + " "
                                    + element.attr("aria-label")
                                    + " "
                                    + element.attr("data-tooltip")
                    ).toLowerCase();

            if (containsRankName(searchable)) {
                System.out.println(
                        limitText(element.outerHtml(), 1000)
                );
            }
        }

        System.out.println(
                "========== OVERWATCH CAREER DEBUG END =========="
        );
    }

    private void printElementsContaining(
            Document document,
            String text
    ) {
        Elements elements =
                document.getElementsContainingOwnText(text);

        System.out.println("검색어: " + text);

        for (Element element : elements) {
            printElementWithParents(element);
        }
    }

    private void printElementWithParents(
            Element element
    ) {
        Element current = element;

        for (int depth = 0;
             depth < 4 && current != null;
             depth++) {

            System.out.println(
                    "depth="
                            + depth
                            + " | "
                            + limitText(
                                    current.outerHtml(),
                                    1500
                            )
            );

            current = current.parent();
        }
    }

    private boolean containsRankName(
            String value
    ) {
        return value.contains("bronze")
                || value.contains("silver")
                || value.contains("gold")
                || value.contains("platinum")
                || value.contains("diamond")
                || value.contains("master")
                || value.contains("grandmaster")
                || value.contains("champion")
                || value.contains("top 500");
    }

    private String limitText(
            String value,
            int maxLength
    ) {
        if (value == null) {
            return "";
        }

        String normalized =
                value.replaceAll("\\s+", " ").trim();

        if (normalized.length() <= maxLength) {
            return normalized;
        }

        return normalized.substring(0, maxLength)
                + "...";
    }

    private boolean isNoProfilePage(
            String pageText
    ) {
        if (pageText == null || pageText.isBlank()) {
            return false;
        }

        String normalized =
                pageText.toLowerCase();

        return normalized.contains("we found no players")
                || normalized.contains(
                        "profile you're searching for is set to public"
                )
                || normalized.contains(
                        "플레이어를 찾지 못했습니다"
                )
                || normalized.contains("프로필이 공개");
    }

    private String toAbsoluteUrl(
            String href
    ) {
        if (href.startsWith("https://")
                || href.startsWith("http://")) {
            return href;
        }

        if (href.startsWith("/")) {
            return OVERWATCH_BASE_URL + href;
        }

        return OVERWATCH_BASE_URL + "/" + href;
    }
}