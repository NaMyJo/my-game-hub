package com.mygamehub.gameidentity;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "game_identity_history",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_game_identity_history_user_uid",
                        columnNames = "user_uid"
                )
        }
)
public class GameIdentityHistory {

    @Id
    @GeneratedValue(
            strategy = GenerationType.IDENTITY
    )
    private Long id;

    @Column(
            name = "user_uid",
            nullable = false
    )
    private String userUid;

    @Column(
            name = "identity_number",
            nullable = false
    )
    private String identityNumber;

    @Column(
            name = "display_name",
            nullable = false
    )
    private String displayName;

    @Column(
            name = "issued_date",
            nullable = false
    )
    private String issuedDate;

    @Column(
            name = "game_power_percent"
    )
    private Double gamePowerPercent;

    @Column(
            name = "evaluation_message",
            length = 500
    )
    private String evaluationMessage;

    @Column(
            name = "snapshot_json",
            columnDefinition = "TEXT",
            nullable = false
    )
    private String snapshotJson;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @Column(name = "share_id", unique = true, length = 36)
    private String shareId;

    @Column(
            name = "share_enabled",
            nullable = false,
            columnDefinition = "boolean default false"
    )
    private boolean shareEnabled;

    protected GameIdentityHistory() {
    }

    public GameIdentityHistory(
            String userUid,
            String identityNumber,
            String displayName,
            String issuedDate,
            Double gamePowerPercent,
            String evaluationMessage,
            String snapshotJson
    ) {
        this.userUid = userUid;
        this.identityNumber = identityNumber;
        this.displayName = displayName;
        this.issuedDate = issuedDate;
        this.gamePowerPercent = gamePowerPercent;
        this.evaluationMessage = evaluationMessage;
        this.snapshotJson = snapshotJson;
        this.updatedAt = Instant.now();
    }

    public void update(
            String identityNumber,
            String displayName,
            String issuedDate,
            Double gamePowerPercent,
            String evaluationMessage,
            String snapshotJson
    ) {
        this.identityNumber = identityNumber;
        this.displayName = displayName;
        this.issuedDate = issuedDate;
        this.gamePowerPercent = gamePowerPercent;
        this.evaluationMessage = evaluationMessage;
        this.snapshotJson = snapshotJson;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getIdentityNumber() {
        return identityNumber;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getIssuedDate() {
        return issuedDate;
    }

    public Double getGamePowerPercent() {
        return gamePowerPercent;
    }

    public String getEvaluationMessage() {
        return evaluationMessage;
    }

    public String getSnapshotJson() {
        return snapshotJson;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateShare(String shareId, boolean enabled) {
        this.shareId = shareId;
        this.shareEnabled = enabled;
        this.updatedAt = Instant.now();
    }

    public String getShareId() { return shareId; }
    public boolean isShareEnabled() { return shareEnabled; }
}
