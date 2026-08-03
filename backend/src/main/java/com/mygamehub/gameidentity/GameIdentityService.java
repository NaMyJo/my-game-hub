package com.mygamehub.gameidentity;

import com.mygamehub.game.GameAccount;
import com.mygamehub.game.GameAccountRepository;
import com.mygamehub.game.GameType;
import com.mygamehub.gameidentity.dto.CreateGameIdentityRequest;
import com.mygamehub.gameidentity.dto.GameIdentityResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class GameIdentityService {

    private final GameIdentityCardRepository identityRepository;
    private final GameAccountRepository gameAccountRepository;

    public GameIdentityService(
            GameIdentityCardRepository identityRepository,
            GameAccountRepository gameAccountRepository
    ) {
        this.identityRepository = identityRepository;
        this.gameAccountRepository = gameAccountRepository;
    }

    @Transactional
    public GameIdentityResponse create(
            String firebaseUid,
            CreateGameIdentityRequest request
    ) {
        String displayName =
                request.displayName().trim();

        Set<Long> distinctIds =
                request.gameAccountIds() == null
                        ? Set.of()
                        : new LinkedHashSet<>(
                                request.gameAccountIds()
                        );

        // 계정이 아예 하나도 없을 때만 차단
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

        GameIdentityEvaluationType evaluationType =
                resolveEvaluationType(
                        hasCompetitive,
                        hasRpg
                );

        /*
         * 현재는 티어 분포 계산 전이므로:
         * 경쟁 게임이 있어도 averageTopPercent는 null
         */
        Double averageTopPercent = null;

        String evaluationMessage =
                createTemporaryEvaluationMessage(
                        displayName,
                        evaluationType
                );

        GameIdentityCard identityCard =
                new GameIdentityCard(
                        firebaseUid,
                        displayName,
                        averageTopPercent,
                        evaluationType,
                        evaluationMessage
                );

        for (int i = 0; i < accounts.size(); i++) {
            GameAccount account = accounts.get(i);

            GameIdentityEntry entry =
                    createEntry(
                            account,
                            i
                    );

            identityCard.addEntry(entry);
        }

        return GameIdentityResponse.from(
                identityRepository.save(identityCard)
        );
    }

    @Transactional(readOnly = true)
    public List<GameIdentityResponse> list(
            String firebaseUid
    ) {
        return identityRepository
                .findAllByFirebaseUidOrderByCreatedAtDesc(
                        firebaseUid
                )
                .stream()
                .map(GameIdentityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public GameIdentityResponse get(
            String firebaseUid,
            Long id
    ) {
        GameIdentityCard card =
                identityRepository
                        .findByIdAndFirebaseUid(
                                id,
                                firebaseUid
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "게임 신분증을 찾을 수 없습니다."
                                )
                        );

        return GameIdentityResponse.from(card);
    }

    @Transactional
    public void delete(
            String firebaseUid,
            Long id
    ) {
        GameIdentityCard card =
                identityRepository
                        .findByIdAndFirebaseUid(
                                id,
                                firebaseUid
                        )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "게임 신분증을 찾을 수 없습니다."
                                )
                        );

        identityRepository.delete(card);
    }

    private GameIdentityEntry createEntry(
            GameAccount account,
            int displayOrder
    ) {
        boolean rpg =
                isRpgGame(account.getGameType());

        String metricLabel;
        String metricValue;

        if (rpg) {
            metricLabel = "전투력";
            metricValue =
                    findMetricValue(
                            account,
                            "전투력"
                    );
        } else {
            metricLabel =
                    account.getPrimaryLabel() == null
                            ? "티어"
                            : account.getPrimaryLabel();

            metricValue =
                    account.getPrimaryValue() == null
                            ? "-"
                            : account.getPrimaryValue();
        }

        return new GameIdentityEntry(
                account.getId(),
                account.getGameType(),
                account.getAccountName(),
                metricLabel,
                metricValue,
                null,
                false,
                displayOrder
        );
    }

    private String findMetricValue(
            GameAccount account,
            String targetLabel
    ) {
        if (targetLabel.equals(
                account.getPrimaryLabel()
        )) {
            return nullToDash(
                    account.getPrimaryValue()
            );
        }

        if (targetLabel.equals(
                account.getSecondaryLabel()
        )) {
            return nullToDash(
                    account.getSecondaryValue()
            );
        }

        if (targetLabel.equals(
                account.getTertiaryLabel()
        )) {
            return nullToDash(
                    account.getTertiaryValue()
            );
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

    private GameIdentityEvaluationType resolveEvaluationType(
            boolean hasCompetitive,
            boolean hasRpg
    ) {
        if (hasCompetitive && hasRpg) {
            return GameIdentityEvaluationType.MIXED;
        }

        if (hasCompetitive) {
            return GameIdentityEvaluationType.COMPETITIVE;
        }

        return GameIdentityEvaluationType.RPG_ONLY;
    }

    private String createTemporaryEvaluationMessage(
            String displayName,
            GameIdentityEvaluationType type
    ) {
        if (type == GameIdentityEvaluationType.RPG_ONLY) {
            return displayName
                    + " 님은 세상을 지키는 모험가시군여!";
        }

        return displayName
                + " 님의 평균 게임력은 계산 준비 중입니다.";
    }

    private String nullToDash(
            String value
    ) {
        return value == null || value.isBlank()
                ? "-"
                : value;
    }
}