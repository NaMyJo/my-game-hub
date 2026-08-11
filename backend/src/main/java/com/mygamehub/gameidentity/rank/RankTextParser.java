package com.mygamehub.gameidentity.rank;

import com.mygamehub.game.GameType;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RankTextParser {

    private static final Pattern ROMAN_DIVISION_PATTERN =
            Pattern.compile(
                    "\\b(IV|III|II|I|V)\\b"
            );

    private static final Pattern NUMBER_DIVISION_PATTERN =
            Pattern.compile(
                    "\\b([1-5])\\b"
            );

    public ParsedRank parse(
            GameType gameType,
            String rankText
    ) {
        if (rankText == null ||
                rankText.isBlank()) {
            return null;
        }

        String normalized =
                normalize(rankText);

        if (isUnranked(normalized)) {
            return null;
        }

        String tier =
                findTier(
                        gameType,
                        normalized
                );

        if (tier == null) {
            return null;
        }

        String division =
                findDivision(normalized);

        if (usesDefaultDivision(
                gameType,
                tier
        )) {
            division = "DEFAULT";
        } else if (division == null) {
            division = "DEFAULT";
        }

        return new ParsedRank(
                tier,
                division
        );
    }

    private String normalize(
            String rankText
    ) {
        return rankText
                .trim()
                .toUpperCase(Locale.ROOT)

                .replace("챌린저", "CHALLENGER")
                .replace("그랜드마스터", "GRANDMASTER")
                .replace("마스터", "MASTER")

                .replace("레디언트", "RADIANT")
                .replace("불멸", "IMMORTAL")
                .replace("초월자", "ASCENDANT")

                .replace("이터니티", "ETERNITY")
                .replace("데미갓", "DEMIGOD")
                .replace("미스릴+", "MITHRIL")
                .replace("미스릴", "MITHRIL")

                .replace("메테오라이트", "METEORITE")

                .replace("서바이버", "SURVIVOR")

                .replace("에메랄드", "EMERALD")
                .replace("다이아몬드", "DIAMOND")
                .replace("플래티넘", "PLATINUM")
                .replace("골드", "GOLD")
                .replace("실버", "SILVER")
                .replace("브론즈", "BRONZE")
                .replace("아이언", "IRON");
    }

    private boolean isUnranked(
            String text
    ) {
        return text.equals("UNRANKED") ||
                text.equals("UNRATED") ||
                text.contains("미진행") ||
                text.contains("배치 전") ||
                text.equals("-");
    }

    private String findTier(
            GameType gameType,
            String text
    ) {
        String[] candidates =
                switch (gameType) {
                    case LEAGUE_OF_LEGENDS, TFT ->
                            new String[]{
                                    "CHALLENGER",
                                    "GRANDMASTER",
                                    "MASTER",
                                    "DIAMOND",
                                    "EMERALD",
                                    "PLATINUM",
                                    "GOLD",
                                    "SILVER",
                                    "BRONZE",
                                    "IRON"
                            };

                    case VALORANT ->
                            new String[]{
                                    "RADIANT",
                                    "IMMORTAL",
                                    "ASCENDANT",
                                    "DIAMOND",
                                    "PLATINUM",
                                    "GOLD",
                                    "SILVER",
                                    "BRONZE",
                                    "IRON"
                            };

                    case BATTLEGROUNDS ->
                            new String[]{
                                    "SURVIVOR",
                                    "MASTER",
                                    "DIAMOND",
                                    "PLATINUM",
                                    "GOLD",
                                    "SILVER",
                                    "BRONZE"
                            };

                    case ETERNAL_RETURN ->
                            new String[]{
                                    "ETERNITY",
                                    "DEMIGOD",
                                    "MITHRIL",
                                    "METEORITE",
                                    "DIAMOND",
                                    "PLATINUM",
                                    "GOLD",
                                    "SILVER",
                                    "BRONZE",
                                    "IRON"
                            };

                    default ->
                            new String[0];
                };

        for (String candidate : candidates) {
            if (text.contains(candidate)) {
                return candidate;
            }
        }

        return null;
    }

    private String findDivision(
            String text
    ) {
        Matcher romanMatcher =
                ROMAN_DIVISION_PATTERN.matcher(text);

        if (romanMatcher.find()) {
            return romanMatcher.group(1);
        }

        Matcher numberMatcher =
                NUMBER_DIVISION_PATTERN.matcher(text);

        if (numberMatcher.find()) {
            return numberToRoman(
                    numberMatcher.group(1)
            );
        }

        return null;
    }

    private String numberToRoman(
            String number
    ) {
        return switch (number) {
            case "1" -> "I";
            case "2" -> "II";
            case "3" -> "III";
            case "4" -> "IV";
            case "5" -> "V";
            default -> "DEFAULT";
        };
    }

    private boolean usesDefaultDivision(
            GameType gameType,
            String tier
    ) {
        if (gameType == GameType.TFT) {
            return true;
        }

        if (gameType == GameType.BATTLEGROUNDS) {
            return true;
        }

        return tier.equals("CHALLENGER") ||
                tier.equals("GRANDMASTER") ||
                tier.equals("MASTER") ||
                tier.equals("RADIANT") ||
                tier.equals("ETERNITY") ||
                tier.equals("DEMIGOD") ||
                tier.equals("MITHRIL");
    }
}