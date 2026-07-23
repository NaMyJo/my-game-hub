package com.mygamehub.game;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "game_accounts")
public class GameAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "firebase_uid", nullable = false, length = 128)
    private String firebaseUid;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_type", nullable = false, length = 40)
    private GameType gameType;

    @Column(nullable = false)
    private String accountName;

    @Column
    private String primaryLabel;

    @Column
    private String primaryValue;

    @Column
    private String secondaryLabel;

    @Column
    private String secondaryValue;

    @Column
    private String tertiaryLabel;

    @Column
    private String tertiaryValue;
    @Column
    private Integer totalGames;

    @Column
    private Double averagePlacement;

    @Column
    private Integer favoriteCharacter1Code;

    @Column
    private Integer favoriteCharacter1Games;

    @Column
    private Double favoriteCharacter1AverageRank;

    @Column
    private Integer favoriteCharacter2Code;

    @Column
    private Integer favoriteCharacter2Games;

    @Column
    private Double favoriteCharacter2AverageRank;

    @Column
    private Integer favoriteCharacter3Code;

    @Column
    private Integer favoriteCharacter3Games;

    @Column
    private Double favoriteCharacter3AverageRank;
    @Column
    private String favoriteCharacter1Name;

    @Column
    private String favoriteCharacter2Name;

    @Column
    private String favoriteCharacter3Name;

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(nullable = false)
    private Instant updatedAt;

    protected GameAccount() {
    }

    public GameAccount(
            String firebaseUid,
            GameType gameType,
            String accountName
    ) {
        this.firebaseUid = firebaseUid;
        this.gameType = gameType;
        this.accountName = accountName;
        this.updatedAt = Instant.now();
    }
    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
        touch();
    }
    
    public void setAccountName(String accountName) {
        this.accountName = accountName;
        touch();
    }

    public void updateStats(
            String primaryLabel,
            String primaryValue,
            String secondaryLabel,
            String secondaryValue,
            String tertiaryLabel,
            String tertiaryValue
    ) {
        this.primaryLabel = primaryLabel;
        this.primaryValue = primaryValue;
        this.secondaryLabel = secondaryLabel;
        this.secondaryValue = secondaryValue;
        this.tertiaryLabel = tertiaryLabel;
        this.tertiaryValue = tertiaryValue;
        touch();
    }

    public void touch() {
        this.updatedAt = Instant.now();
    }
    public void updateEternalReturnStats(
        String tier,
        String score,
        Integer totalGames,
        Double averagePlacement,

        Integer favoriteCharacter1Code,
        String favoriteCharacter1Name,
        Integer favoriteCharacter1Games,
        Double favoriteCharacter1AverageRank,

        Integer favoriteCharacter2Code,
        String favoriteCharacter2Name,
        Integer favoriteCharacter2Games,
        Double favoriteCharacter2AverageRank,

        Integer favoriteCharacter3Code,
        String favoriteCharacter3Name,
        Integer favoriteCharacter3Games,
        Double favoriteCharacter3AverageRank
) {
    this.primaryLabel = "티어";
    this.primaryValue = tier;

    this.secondaryLabel = "점수";
    this.secondaryValue = score;

    this.tertiaryLabel = "판수";
    this.tertiaryValue = totalGames == null ? "-" : totalGames + "판";

    this.totalGames = totalGames;
    this.averagePlacement = averagePlacement;

    this.favoriteCharacter1Code = favoriteCharacter1Code;
    this.favoriteCharacter1Name = favoriteCharacter1Name;
    this.favoriteCharacter1Games = favoriteCharacter1Games;
    this.favoriteCharacter1AverageRank = favoriteCharacter1AverageRank;

    this.favoriteCharacter2Code = favoriteCharacter2Code;
    this.favoriteCharacter2Name = favoriteCharacter2Name;
    this.favoriteCharacter2Games = favoriteCharacter2Games;
    this.favoriteCharacter2AverageRank = favoriteCharacter2AverageRank;

    this.favoriteCharacter3Code = favoriteCharacter3Code;
    this.favoriteCharacter3Name = favoriteCharacter3Name;
    this.favoriteCharacter3Games = favoriteCharacter3Games;
    this.favoriteCharacter3AverageRank = favoriteCharacter3AverageRank;

    touch();
}
    public Long getId() { return id; }
    public String getFirebaseUid() { return firebaseUid; }
    public GameType getGameType() { return gameType; }
    public String getAccountName() { return accountName; }
    public String getPrimaryLabel() { return primaryLabel; }
    public String getPrimaryValue() { return primaryValue; }
    public String getSecondaryLabel() { return secondaryLabel; }
    public String getSecondaryValue() { return secondaryValue; }
    public String getTertiaryLabel() { return tertiaryLabel; }
    public String getTertiaryValue() { return tertiaryValue; }
    public Instant getUpdatedAt() { return updatedAt; }
    
    public Integer getTotalGames() {
    return totalGames;
    }

    public Double getAveragePlacement() {
        return averagePlacement;
    }

    public Integer getFavoriteCharacter1Code() {
    return favoriteCharacter1Code;
    }

    public Integer getFavoriteCharacter1Games() {
        return favoriteCharacter1Games;
    }

    public Double getFavoriteCharacter1AverageRank() {
        return favoriteCharacter1AverageRank;
    }

    public Integer getFavoriteCharacter2Code() {
        return favoriteCharacter2Code;
    }

    public Integer getFavoriteCharacter2Games() {
        return favoriteCharacter2Games;
    }

    public Double getFavoriteCharacter2AverageRank() {
        return favoriteCharacter2AverageRank;
    }

    public Integer getFavoriteCharacter3Code() {
        return favoriteCharacter3Code;
    }

    public Integer getFavoriteCharacter3Games() {
        return favoriteCharacter3Games;
    }

    public Double getFavoriteCharacter3AverageRank() {
        return favoriteCharacter3AverageRank;
    }
    public String getFavoriteCharacter1Name() {
    return favoriteCharacter1Name;
    }

    public String getFavoriteCharacter2Name() {
        return favoriteCharacter2Name;
    }

    public String getFavoriteCharacter3Name() {
        return favoriteCharacter3Name;
    }
    
}
