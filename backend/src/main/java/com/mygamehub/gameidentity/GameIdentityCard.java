package com.mygamehub.gameidentity;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "game_identity_cards")
public class GameIdentityCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "firebase_uid",
            nullable = false,
            length = 128
    )
    private String firebaseUid;

    @Column(
            name = "display_name",
            nullable = false,
            length = 20
    )
    private String displayName;

    /**
     * 경쟁 게임이 하나 이상 있을 때만 저장됩니다.
     * RPG 게임만 있다면 null입니다.
     */
    @Column(name = "average_top_percent")
    private Double averageTopPercent;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "evaluation_type",
            nullable = false,
            length = 30
    )
    private GameIdentityEvaluationType evaluationType;

    @Column(
            name = "evaluation_message",
            nullable = false,
            length = 255
    )
    private String evaluationMessage;

    @Column(
            name = "image_url",
            length = 2048
    )
    private String imageUrl;

    @Column(
            name = "created_at",
            nullable = false
    )
    private Instant createdAt;

    @Column(
            name = "updated_at",
            nullable = false
    )
    private Instant updatedAt;

    @OneToMany(
            mappedBy = "identityCard",
            cascade = CascadeType.ALL,
            orphanRemoval = true
    )
    @OrderBy("displayOrder ASC")
    private List<GameIdentityEntry> entries =
            new ArrayList<>();

    protected GameIdentityCard() {
    }

    public GameIdentityCard(
            String firebaseUid,
            String displayName,
            Double averageTopPercent,
            GameIdentityEvaluationType evaluationType,
            String evaluationMessage
    ) {
        this.firebaseUid = firebaseUid;
        this.displayName = displayName;
        this.averageTopPercent = averageTopPercent;
        this.evaluationType = evaluationType;
        this.evaluationMessage = evaluationMessage;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public void addEntry(
            GameIdentityEntry entry
    ) {
        entries.add(entry);
        entry.setIdentityCard(this);
        touch();
    }

    public void setImageUrl(
            String imageUrl
    ) {
        this.imageUrl = imageUrl;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public Double getAverageTopPercent() {
        return averageTopPercent;
    }

    public GameIdentityEvaluationType getEvaluationType() {
        return evaluationType;
    }

    public String getEvaluationMessage() {
        return evaluationMessage;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public List<GameIdentityEntry> getEntries() {
        return entries;
    }
}