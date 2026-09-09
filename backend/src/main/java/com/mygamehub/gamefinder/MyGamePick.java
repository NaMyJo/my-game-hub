package com.mygamehub.gamefinder;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_game_picks",
        uniqueConstraints = @UniqueConstraint(name = "uk_game_pick_user_app",
                columnNames = {"firebase_uid", "steam_app_id"}),
        indexes = @Index(name = "idx_game_pick_user_created",
                columnList = "firebase_uid,created_at"))
public class MyGamePick {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;
    @Column(name = "steam_app_id", nullable = false)
    private Long steamAppId;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MyGamePick() {}

    public MyGamePick(String firebaseUid, long steamAppId, Instant createdAt) {
        this.firebaseUid = firebaseUid;
        this.steamAppId = steamAppId;
        this.createdAt = createdAt;
    }

    public Long getSteamAppId() { return steamAppId; }
    public Instant getCreatedAt() { return createdAt; }
}
