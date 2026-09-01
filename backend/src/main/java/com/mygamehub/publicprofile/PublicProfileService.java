package com.mygamehub.publicprofile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.gameidentity.GameIdentityHistory;
import com.mygamehub.gameidentity.GameIdentityHistoryService;
import com.mygamehub.gameidentity.GameIdentityPreviewService;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewResponse;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewEntryResponse;
import com.mygamehub.publicprofile.dto.*;
import com.mygamehub.user.AppUser;
import com.mygamehub.user.AppUserRepository;
import com.mygamehub.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class PublicProfileService {
    private final AppUserRepository userRepository;
    private final UserService userService;
    private final GameIdentityPreviewService previewService;
    private final GameIdentityHistoryService historyService;
    private final ObjectMapper objectMapper;

    public PublicProfileService(
            AppUserRepository userRepository,
            UserService userService,
            GameIdentityPreviewService previewService,
            GameIdentityHistoryService historyService,
            ObjectMapper objectMapper
    ) {
        this.userRepository = userRepository;
        this.userService = userService;
        this.previewService = previewService;
        this.historyService = historyService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PublicProfileSettingsResponse settings(AuthenticatedUser authUser) {
        return settingsResponse(userService.sync(authUser));
    }

    @Transactional
    public PublicProfileSettingsResponse updateSettings(
            AuthenticatedUser authUser,
            boolean isPublic
    ) {
        AppUser user = userService.sync(authUser);
        String publicId = user.getPublicId() == null
                ? UUID.randomUUID().toString()
                : user.getPublicId();
        user.updatePublicProfile(publicId, isPublic);
        return settingsResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Optional<PublicProfileResponse> findPublic(String publicId) {
        return userRepository.findByPublicIdAndProfilePublicTrue(publicId)
                .map(user -> {
                    String nickname = firstNonBlank(
                            user.getProfileNickname(), user.getDisplayName(), "게이머"
                    );
                    GameIdentityPreviewResponse analysis = previewService.analyzeAll(
                            user.getFirebaseUid(), nickname
                    );
                    PublicIdentityResponse latest = historyService
                            .findEntityByUserUid(user.getFirebaseUid())
                            .map(this::publicIdentity)
                            .orElse(null);
                    return new PublicProfileResponse(
                            user.getPublicId(), nickname,
                            firstNonBlank(user.getProfileIntroduction(), ""),
                            publicAnalysis(analysis), latest
                    );
                });
    }

    public GameIdentityPreviewResponse analysis(AuthenticatedUser authUser) {
        AppUser user = userService.sync(authUser);
        String nickname = firstNonBlank(
                user.getProfileNickname(), authUser.name(), "게이머"
        );
        return previewService.analyzeAll(authUser.uid(), nickname);
    }

    public IdentityShareResponse enableShare(String userUid) {
        return shareResponse(historyService.enableShare(userUid));
    }

    public IdentityShareResponse disableShare(String userUid) {
        return shareResponse(historyService.disableShare(userUid));
    }

    public Optional<IdentityShareResponse> shareSettings(String userUid) {
        return historyService.findEntityByUserUid(userUid).map(this::shareResponse);
    }

    public Optional<PublicIdentityResponse> findSharedIdentity(String shareId) {
        return historyService.findShared(shareId).map(this::publicIdentity);
    }

    private PublicProfileSettingsResponse settingsResponse(AppUser user) {
        return new PublicProfileSettingsResponse(
                user.getPublicId(), user.isProfilePublic()
        );
    }

    private PublicIdentityResponse publicIdentity(GameIdentityHistory entity) {
        return new PublicIdentityResponse(
                entity.getShareId(), entity.getIdentityNumber(),
                entity.getDisplayName(), entity.getIssuedDate(),
                entity.getGamePowerPercent(), entity.getEvaluationMessage(),
                publicSnapshot(entity.getSnapshotJson())
        );
    }

    private IdentityShareResponse shareResponse(GameIdentityHistory entity) {
        return new IdentityShareResponse(
                entity.getShareId(), entity.isShareEnabled(),
                entity.getIdentityNumber(), entity.getDisplayName(),
                entity.getIssuedDate(), entity.getGamePowerPercent(),
                entity.getEvaluationMessage(), publicSnapshot(entity.getSnapshotJson())
        );
    }

    private String publicSnapshot(String snapshotJson) {
        try {
            JsonNode root = objectMapper.readTree(snapshotJson);
            JsonNode games = root.path("selectedGames");
            if (games.isArray()) {
                games.forEach(game -> {
                    if (game instanceof ObjectNode object) object.remove("id");
                });
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            return "{}";
        }
    }

    private GameIdentityPreviewResponse publicAnalysis(
            GameIdentityPreviewResponse analysis
    ) {
        return new GameIdentityPreviewResponse(
                analysis.displayName(), analysis.averageTopPercent(),
                analysis.evaluationType(), analysis.evaluationMessage(),
                analysis.includedGameCount(), analysis.games().stream()
                .map(entry -> new GameIdentityPreviewEntryResponse(
                        null, entry.gameType(), entry.accountName(),
                        entry.metricLabel(), entry.metricValue(),
                        entry.topPercent(), entry.includedInAverage(),
                        entry.estimated(), entry.exclusionReason()
                )).toList()
        );
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) return value;
        }
        return "";
    }
}
