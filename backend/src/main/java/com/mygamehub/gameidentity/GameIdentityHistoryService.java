package com.mygamehub.gameidentity;

import com.mygamehub.gameidentity.dto.GameIdentityHistoryRequest;
import com.mygamehub.gameidentity.dto.GameIdentityHistoryResponse;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class GameIdentityHistoryService {

    private final GameIdentityHistoryRepository repository;

    public GameIdentityHistoryService(
            GameIdentityHistoryRepository repository
    ) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<GameIdentityHistoryResponse> getLatest(
            String userUid
    ) {
        return repository
                .findByUserUid(userUid)
                .map(this::toResponse);
    }

    @Transactional
    public GameIdentityHistoryResponse save(
            String userUid,
            GameIdentityHistoryRequest request
    ) {
        GameIdentityHistory entity =
                repository
                        .findByUserUid(userUid)
                        .orElse(null);

        if (entity == null) {
            entity = new GameIdentityHistory(
                    userUid,
                    request.identityNumber(),
                    request.displayName(),
                    request.issuedDate(),
                    request.gamePowerPercent(),
                    request.evaluationMessage(),
                    request.snapshotJson()
            );
        } else {
            entity.update(
                    request.identityNumber(),
                    request.displayName(),
                    request.issuedDate(),
                    request.gamePowerPercent(),
                    request.evaluationMessage(),
                    request.snapshotJson()
            );
        }

        GameIdentityHistory saved =
                repository.save(entity);

        return toResponse(saved);
    }

    @Transactional
    public GameIdentityHistory enableShare(String userUid) {
        GameIdentityHistory entity = repository.findByUserUid(userUid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "공유할 최근 게임 신분증이 없습니다."
                ));
        String shareId = entity.getShareId() == null
                ? UUID.randomUUID().toString()
                : entity.getShareId();
        entity.updateShare(shareId, true);
        return repository.save(entity);
    }

    @Transactional
    public GameIdentityHistory disableShare(String userUid) {
        GameIdentityHistory entity = repository.findByUserUid(userUid)
                .orElseThrow(() -> new IllegalArgumentException(
                        "공유할 최근 게임 신분증이 없습니다."
                ));
        entity.updateShare(entity.getShareId(), false);
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public Optional<GameIdentityHistory> findShared(String shareId) {
        return repository.findByShareIdAndShareEnabledTrue(shareId);
    }

    @Transactional(readOnly = true)
    public Optional<GameIdentityHistory> findEntityByUserUid(String userUid) {
        return repository.findByUserUid(userUid);
    }

    private GameIdentityHistoryResponse toResponse(
            GameIdentityHistory entity
    ) {
        return new GameIdentityHistoryResponse(
                entity.getId(),
                entity.getIdentityNumber(),
                entity.getDisplayName(),
                entity.getIssuedDate(),
                entity.getGamePowerPercent(),
                entity.getEvaluationMessage(),
                entity.getSnapshotJson(),
                entity.getUpdatedAt()
        );
    }
}
