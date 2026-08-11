package com.mygamehub.gameprofile;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "game_profile_summary")
public class GameProfileSummary {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "user_uid",
            nullable = false,
            unique = true,
            length = 128
    )
    private String userUid;

    @Column(
            name = "identity_nickname",
            nullable = false,
            length = 50
    )
    private String identityNickname;

    @Column(name = "game_power_percent")
    private Double gamePowerPercent;

    @Column(
            name = "reflected_game_count",
            nullable = false
    )
    private Integer reflectedGameCount;

    @Column(
            name = "evaluation_message",
            length = 255
    )
    private String evaluationMessage;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    protected GameProfileSummary() {
    }

    public GameProfileSummary(
            String userUid,
            String identityNickname,
            Double gamePowerPercent,
            Integer reflectedGameCount,
            String evaluationMessage
    ) {
        this.userUid = userUid;
        this.identityNickname = identityNickname;
        this.gamePowerPercent = gamePowerPercent;
        this.reflectedGameCount =
                reflectedGameCount == null
                        ? 0
                        : reflectedGameCount;
        this.evaluationMessage = evaluationMessage;
        this.updatedAt = Instant.now();
    }

    public void update(
            String identityNickname,
            Double gamePowerPercent,
            Integer reflectedGameCount,
            String evaluationMessage
    ) {
        this.identityNickname = identityNickname;
        this.gamePowerPercent = gamePowerPercent;
        this.reflectedGameCount =
                reflectedGameCount == null
                        ? 0
                        : reflectedGameCount;
        this.evaluationMessage = evaluationMessage;
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserUid() {
        return userUid;
    }

    public String getIdentityNickname() {
        return identityNickname;
    }

    public Double getGamePowerPercent() {
        return gamePowerPercent;
    }

    public Integer getReflectedGameCount() {
        return reflectedGameCount;
    }

    public String getEvaluationMessage() {
        return evaluationMessage;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}