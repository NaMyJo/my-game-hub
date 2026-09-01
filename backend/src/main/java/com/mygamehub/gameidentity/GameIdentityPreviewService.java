package com.mygamehub.gameidentity;

import com.mygamehub.game.GameAccount;
import com.mygamehub.game.GameAccountRepository;
import com.mygamehub.game.GameType;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewEntryResponse;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewRequest;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewResponse;
import com.mygamehub.gameidentity.rank.GamePowerEvaluation;
import com.mygamehub.gameidentity.rank.GamePowerEvaluationService;
import com.mygamehub.gameidentity.rank.RankPercentileResult;
import com.mygamehub.gameidentity.rank.RankPercentileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GameIdentityPreviewService {

    private final GameAccountRepository gameAccountRepository;
    private final RankPercentileService rankPercentileService;
    private final GamePowerEvaluationService evaluationService;

    public GameIdentityPreviewService(
            GameAccountRepository gameAccountRepository,
            RankPercentileService rankPercentileService,
            GamePowerEvaluationService evaluationService
    ) {
        this.gameAccountRepository = gameAccountRepository;
        this.rankPercentileService = rankPercentileService;
        this.evaluationService = evaluationService;
    }

    @Transactional(readOnly = true)
    public GameIdentityPreviewResponse preview(
            String firebaseUid,
            GameIdentityPreviewRequest request
    ) {
        String displayName =
                request.displayName().trim();

        Set<Long> distinctIds =
                request.gameAccountIds() == null
                        ? Set.of()
                        : new LinkedHashSet<>(
                                request.gameAccountIds()
                        );

        if (distinctIds.isEmpty()) {
            throw new IllegalArgumentException(
                    "게임 신분증에 사용할 게임 계정을 하나 이상 선택해주세요."
            );
        }

        List<GameAccount> accounts =
                distinctIds.stream()
                        .map(id ->
                                gameAccountRepository
                                        .findByIdAndFirebaseUid(
                                                id,
                                                firebaseUid
                                        )
                                        .orElseThrow(() ->
                                                new IllegalArgumentException(
                                                        "선택한 게임 계정을 찾을 수 없습니다."
                                                )
                                        )
                        )
                        .toList();

        List<GameIdentityPreviewEntryResponse> entries =
                accounts.stream()
                        .map(this::createEntry)
                        .toList();

        GamePowerEvaluation evaluation =
                evaluationService.evaluate(
                        displayName,
                        accounts
                );

        boolean hasCompetitive =
                accounts.stream()
                        .anyMatch(account ->
                                isCompetitiveGame(
                                        account.getGameType()
                                )
                        );

        boolean hasRpg =
                accounts.stream()
                        .anyMatch(account ->
                                isRpgGame(
                                        account.getGameType()
                                )
                        );

        String evaluationType =
                resolveEvaluationType(
                        hasCompetitive,
                        hasRpg,
                        evaluation.includedGameCount()
                );

        String evaluationMessage =
                resolveEvaluationMessage(
                        displayName,
                        evaluationType,
                        evaluation
                );

        return new GameIdentityPreviewResponse(
                displayName,
                evaluation.averageTopPercent(),
                evaluationType,
                evaluationMessage,
                evaluation.includedGameCount(),
                entries
        );
    }

    @Transactional(readOnly = true)
    public GameIdentityPreviewResponse analyzeAll(
            String firebaseUid,
            String displayName
    ) {
        List<Long> ids = gameAccountRepository
                .findAllByFirebaseUidOrderByDisplayOrderAscIdAsc(firebaseUid)
                .stream()
                .map(GameAccount::getId)
                .toList();

        if (ids.isEmpty()) {
            return new GameIdentityPreviewResponse(
                    displayName, null, "RPG_ONLY",
                    "등록된 게임 계정이 없습니다.", 0, List.of()
            );
        }

        return preview(firebaseUid, new GameIdentityPreviewRequest(displayName, ids));
    }

    private GameIdentityPreviewEntryResponse createEntry(
            GameAccount account
    ) {
        // RPG 게임은 상위 퍼센트 계산에서 제외
        if (isRpgGame(account.getGameType())) {
            return new GameIdentityPreviewEntryResponse(
                    account.getId(),
                    account.getGameType(),
                    account.getAccountName(),
                    "전투력",
                    findMetricValue(
                            account,
                            "전투력"
                    ),
                    null,
                    false,
                    false,
                    "RPG 게임"
            );
        }

        // 경쟁 게임의 티어와 상위 퍼센트 계산
        RankPercentileResult percentile =
                rankPercentileService.calculate(
                        account
                );

        String metricLabel =
                findRankLabel(account);

        String metricValue =
                percentile.originalRank() == null ||
                percentile.originalRank().isBlank()
                        ? findRankValue(account)
                        : percentile.originalRank();

        return new GameIdentityPreviewEntryResponse(
                account.getId(),
                account.getGameType(),
                account.getAccountName(),
                metricLabel,
                nullToDash(metricValue),
                percentile.topPercent(),
                percentile.available(),
                percentile.estimated(),
                percentile.available()
                        ? null
                        : resolveExclusionReason(account)
        );
    }

    private String resolveExclusionReason(GameAccount account) {
        if (account.getGameType() == GameType.ETERNAL_RETURN &&
                ("0 RP".equals(account.getSecondaryValue()) ||
                        "0".equals(account.getSecondaryValue()))) {
            return "경쟁전 미진행";
        }
        return "비교 가능한 랭크 데이터 없음";
    }
private String findRankLabel(
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
            return label;
        }
    }

    return account.getPrimaryLabel() == null ||
            account.getPrimaryLabel().isBlank()
            ? "티어"
            : account.getPrimaryLabel();
}

private String findRankValue(
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

    return nullToDash(
            account.getPrimaryValue()
    );
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
    private String resolveEvaluationType(
            boolean hasCompetitive,
            boolean hasRpg,
            int includedGameCount
    ) {
        if (hasCompetitive && hasRpg) {
            if (includedGameCount > 0) {
                return "MIXED";
            }

            return "MIXED_UNAVAILABLE";
        }

        if (hasCompetitive) {
            if (includedGameCount > 0) {
                return "COMPETITIVE";
            }

            return "UNRANKED_ONLY";
        }

        return "RPG_ONLY";
    }
   private String resolveEvaluationMessage(
            String displayName,
            String evaluationType,
            GamePowerEvaluation evaluation
    ) {
        return switch (evaluationType) {
            case "RPG_ONLY" ->
                    displayName
                            + " 님은 세상을 지키는 모험가시군여!";

            case "UNRANKED_ONLY" ->
                    displayName
                            + " 님은 아직 티어가 정해지지 않은 도전자시군여!";

            case "MIXED_UNAVAILABLE" ->
                    displayName
                            + " 님은 모험과 경쟁을 함께 즐기는 게이머시군여!";

            default ->
                    evaluation.message();
        };
    }

    private String findMetricValue(
            GameAccount account,
            String targetLabel
    ) {
        if (targetLabel.equals(account.getPrimaryLabel())) {
            return nullToDash(account.getPrimaryValue());
        }

        if (targetLabel.equals(account.getSecondaryLabel())) {
            return nullToDash(account.getSecondaryValue());
        }

        if (targetLabel.equals(account.getTertiaryLabel())) {
            return nullToDash(account.getTertiaryValue());
        }

        return "-";
    }

    private boolean isRpgGame(
            GameType gameType
    ) {
        return gameType == GameType.LOST_ARK ||
                gameType == GameType.MAPLE_STORY ||
                gameType == GameType.DUNGEON_FIGHTER;
    }

    private boolean isCompetitiveGame(
            GameType gameType
    ) {
        return gameType == GameType.LEAGUE_OF_LEGENDS ||
                gameType == GameType.TFT ||
                gameType == GameType.ETERNAL_RETURN ||
                gameType == GameType.BATTLEGROUNDS ||
                gameType == GameType.VALORANT;
    }

    private String nullToDash(
            String value
    ) {
        return value == null || value.isBlank()
                ? "-"
                : value;
    }
}
