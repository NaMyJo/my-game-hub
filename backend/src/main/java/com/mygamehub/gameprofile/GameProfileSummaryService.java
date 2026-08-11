package com.mygamehub.gameprofile;

import com.mygamehub.gameprofile.dto.GameProfileSummaryRequest;
import com.mygamehub.gameprofile.dto.GameProfileSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class GameProfileSummaryService {

    private final GameProfileSummaryRepository repository;

    public GameProfileSummaryService(
            GameProfileSummaryRepository repository
    ) {
        this.repository = repository;
    }

    // ==============================
    // 게임 프로필 조회
    // ==============================

    @Transactional(readOnly = true)
    public Optional<GameProfileSummaryResponse> get(
            String userUid
    ) {
        return repository
                .findByUserUid(userUid)
                .map(GameProfileSummaryResponse::from);
    }

    // ==============================
    // 게임 프로필 저장 / 수정
    // ==============================

    @Transactional
    public GameProfileSummaryResponse save(
            String userUid,
            GameProfileSummaryRequest request
    ) {

        GameProfileSummary profile =
                repository
                        .findByUserUid(userUid)
                        .orElseGet(
                                () -> new GameProfileSummary(
                                        userUid,
                                        request.identityNickname(),
                                        request.gamePowerPercent(),
                                        request.reflectedGameCount(),
                                        request.evaluationMessage()
                                )
                        );

        // 기존 데이터인 경우 UPDATE
        if (profile.getId() != null) {
            profile.update(
                    request.identityNickname(),
                    request.gamePowerPercent(),
                    request.reflectedGameCount(),
                    request.evaluationMessage()
            );
        }

        GameProfileSummary saved =
                repository.save(profile);

        return GameProfileSummaryResponse.from(saved);
    }
}