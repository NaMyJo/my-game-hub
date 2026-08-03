package com.mygamehub.gameidentity.rank;

import com.mygamehub.game.GameAccount;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class GamePowerEvaluationService {

    private final RankPercentileService percentileService;

    public GamePowerEvaluationService(
            RankPercentileService percentileService
    ) {
        this.percentileService = percentileService;
    }

    public GamePowerEvaluation evaluate(
            String displayName,
            List<GameAccount> accounts
    ) {
        List<RankPercentileResult> availableResults =
                accounts.stream()
                        .map(percentileService::calculate)
                        .filter(
                                RankPercentileResult::available
                        )
                        .toList();

        if (availableResults.isEmpty()) {
            return new GamePowerEvaluation(
                    null,
                    displayName
                            + " 님은 세상을 지키는 모험가시군여!",
                    0,
                    false
            );
        }

        double average =
                availableResults.stream()
                        .mapToDouble(
                                RankPercentileResult::topPercent
                        )
                        .average()
                        .orElseThrow();

        double rounded =
                Math.round(average * 10.0) / 10.0;

        boolean estimated =
                availableResults.stream()
                        .anyMatch(
                                RankPercentileResult::estimated
                        );

        String percentPrefix =
                estimated
                        ? "상위 약 "
                        : "상위 ";

        String message =
                displayName
                        + " 님의 평균 게임력은 "
                        + percentPrefix
                        + formatPercent(rounded)
                        + "%입니다.\n"
                        + comment(rounded);

        return new GamePowerEvaluation(
                rounded,
                message,
                availableResults.size(),
                estimated
        );
    }

    private String comment(
            double topPercent
    ) {
        if (topPercent < 1.0) {
            return "게임계의 최정상에 계시는군여!";
        }

        if (topPercent < 3.0) {
            return "엄청난 실력이에요. 고수의 기운이 느껴져요!";
        }

        if (topPercent < 5.0) {
            return "게임을 참 잘하시는군여!";
        }

        if (topPercent < 10.0) {
            return "상당한 실력자시군여!";
        }

        if (topPercent < 20.0) {
            return "평균을 훌쩍 넘는 좋은 실력이에요!";
        }

        if (topPercent < 30.0) {
            return "꾸준히 게임을 즐겨온 실력자시군여!";
        }

        if (topPercent < 50.0) {
            return "조금만 더 노력하면 상위권에 도달할 수 있어요!";
        }

        return "아직 조금 더 노력이 필요하겠어요!";
    }

    private String formatPercent(
            double percent
    ) {
        if (percent == Math.floor(percent)) {
            return String.format(
                    "%.0f",
                    percent
            );
        }

        return String.format(
                "%.1f",
                percent
        );
    }
}