package com.mygamehub.gameidentity.rank;

import com.mygamehub.game.GameAccount;
import com.mygamehub.game.GameType;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class RankPercentileService {

    private final RankDistributionLoader loader;
    private final RankTextParser parser;

    public RankPercentileService(
            RankDistributionLoader loader,
            RankTextParser parser
    ) {
        this.loader = loader;
        this.parser = parser;
    }

    public RankPercentileResult calculate(
            GameAccount account
    ) {
        GameType gameType =
                account.getGameType();

        String rankText =
                findRankText(account);

        if (!supports(gameType)) {
            return RankPercentileResult.unavailable(
                    gameType,
                    rankText
            );
        }

        ParsedRank parsed =
                parser.parse(
                        gameType,
                        rankText
                );

        if (parsed == null) {
            return RankPercentileResult.unavailable(
                    gameType,
                    rankText
            );
        }

        RankDistributionConfig config =
                loader.load();

        if (config == null ||
                config.games() == null) {
            return RankPercentileResult.unavailable(
                    gameType,
                    rankText
            );
        }

        RankDistributionConfig.GameDistribution game =
                config.games().get(
                        gameType.name()
                );

        if (game == null ||
                game.tiers() == null) {
            return RankPercentileResult.unavailable(
                    gameType,
                    rankText
            );
        }

        Map<String, Double> divisions =
                game.tiers().get(
                        parsed.tier()
                );

        if (divisions == null ||
                divisions.isEmpty()) {
            return RankPercentileResult.unavailable(
                    gameType,
                    rankText
            );
        }

        Double topPercent =
                divisions.get(
                        parsed.division()
                );

        if (topPercent == null) {
            topPercent =
                    divisions.get("DEFAULT");
        }

        if (topPercent == null) {
            return RankPercentileResult.unavailable(
                    gameType,
                    rankText
            );
        }

        return new RankPercentileResult(
                gameType,
                rankText,
                parsed.tier(),
                parsed.division(),
                topPercent,
                game.season(),
                game.region(),
                true,
                game.isEstimated()
        );
    }

    private String findRankText(
            GameAccount account
    ) {
        String[] labels =
                switch (account.getGameType()) {
                    case LEAGUE_OF_LEGENDS ->
                            new String[]{
                                    "솔로랭크",
                                    "티어"
                            };

                    case TFT ->
                            new String[]{
                                    "티어"
                            };

                    case ETERNAL_RETURN ->
                            new String[]{
                                    "티어"
                            };

                    case BATTLEGROUNDS ->
                            new String[]{
                                    "랭크 티어",
                                    "티어"
                            };

                    case VALORANT ->
                            new String[]{
                                    "경쟁전 티어",
                                    "티어"
                            };

                    default ->
                            new String[0];
                };

        for (String label : labels) {
            String value =
                    findValueByLabel(
                            account,
                            label
                    );

            if (value != null &&
                    !value.isBlank()) {
                return value;
            }
        }

        return account.getPrimaryValue();
    }

    private String findValueByLabel(
            GameAccount account,
            String targetLabel
    ) {
        if (targetLabel.equals(
                account.getPrimaryLabel()
        )) {
            return account.getPrimaryValue();
        }

        if (targetLabel.equals(
                account.getSecondaryLabel()
        )) {
            return account.getSecondaryValue();
        }

        if (targetLabel.equals(
                account.getTertiaryLabel()
        )) {
            return account.getTertiaryValue();
        }

        return null;
    }

    private boolean supports(
            GameType gameType
    ) {
        return gameType ==
                GameType.LEAGUE_OF_LEGENDS ||
                gameType == GameType.TFT ||
                gameType ==
                        GameType.ETERNAL_RETURN ||
                gameType ==
                        GameType.BATTLEGROUNDS ||
                gameType == GameType.VALORANT;
    }
}