package com.mygamehub.gameidentity;

import com.mygamehub.game.GameType;
import jakarta.persistence.*;

@Entity
@Table(name = "game_identity_entries")
public class GameIdentityEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(
            fetch = FetchType.LAZY,
            optional = false
    )
    @JoinColumn(
            name = "identity_card_id",
            nullable = false
    )
    private GameIdentityCard identityCard;

    /**
     * 신분증을 생성할 때 사용한 원본 게임 카드 ID
     */
    @Column(
            name = "game_account_id",
            nullable = false
    )
    private Long gameAccountId;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "game_type",
            nullable = false,
            length = 40
    )
    private GameType gameType;

    @Column(
            name = "account_name",
            nullable = false
    )
    private String accountName;

    /**
     * 경쟁 게임: 티어, 랭크 티어
     * RPG 게임: 전투력
     */
    @Column(
            name = "metric_label",
            nullable = false,
            length = 40
    )
    private String metricLabel;

    @Column(
            name = "metric_value",
            nullable = false
    )
    private String metricValue;

    /**
     * 경쟁 게임만 사용합니다.
     * RPG 게임은 null입니다.
     */
    @Column(name = "top_percent")
    private Double topPercent;

    @Column(
            name = "included_in_average",
            nullable = false
    )
    private boolean includedInAverage;

    @Column(
            name = "display_order",
            nullable = false
    )
    private Integer displayOrder;

    protected GameIdentityEntry() {
    }

    public GameIdentityEntry(
            Long gameAccountId,
            GameType gameType,
            String accountName,
            String metricLabel,
            String metricValue,
            Double topPercent,
            boolean includedInAverage,
            Integer displayOrder
    ) {
        this.gameAccountId = gameAccountId;
        this.gameType = gameType;
        this.accountName = accountName;
        this.metricLabel = metricLabel;
        this.metricValue = metricValue;
        this.topPercent = topPercent;
        this.includedInAverage = includedInAverage;
        this.displayOrder = displayOrder;
    }

    void setIdentityCard(
            GameIdentityCard identityCard
    ) {
        this.identityCard = identityCard;
    }

    public Long getId() {
        return id;
    }

    public Long getGameAccountId() {
        return gameAccountId;
    }

    public GameType getGameType() {
        return gameType;
    }

    public String getAccountName() {
        return accountName;
    }

    public String getMetricLabel() {
        return metricLabel;
    }

    public String getMetricValue() {
        return metricValue;
    }

    public Double getTopPercent() {
        return topPercent;
    }

    public boolean isIncludedInAverage() {
        return includedInAverage;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }
}