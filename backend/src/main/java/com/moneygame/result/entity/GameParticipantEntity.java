package com.moneygame.result.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/** 참가자별 최종 결과. 봇은 userId 가 null 이고 bot = true (2026-10-03 결정). */
@Entity
@Table(name = "game_participants")
public class GameParticipantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "game_id", nullable = false)
    private Long gameId;

    /** 엔진의 플레이어 ID. 사람은 users.id 문자열, 봇은 bot-N */
    @Column(name = "player_key", nullable = false, length = 40)
    private String playerKey;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "nickname", nullable = false, length = 20)
    private String nickname;

    @Column(name = "is_bot", nullable = false)
    private Boolean bot;

    @Column(name = "final_rank", nullable = false)
    private Integer finalRank;

    @Column(name = "final_asset", nullable = false, precision = 24, scale = 8)
    private BigDecimal finalAsset;

    @Column(name = "return_rate", nullable = false, precision = 12, scale = 4)
    private BigDecimal returnRate;

    @Column(name = "trade_count", nullable = false)
    private Integer tradeCount;

    @Column(name = "liquidated_count", nullable = false)
    private Integer liquidatedCount;

    protected GameParticipantEntity() {
    }

    public GameParticipantEntity(Long gameId, String playerKey, Long userId, String nickname, boolean bot,
                                 int finalRank, BigDecimal finalAsset, BigDecimal returnRate,
                                 int tradeCount, int liquidatedCount) {
        this.gameId = gameId;
        this.playerKey = playerKey;
        this.userId = userId;
        this.nickname = nickname;
        this.bot = bot;
        this.finalRank = finalRank;
        this.finalAsset = finalAsset;
        this.returnRate = returnRate;
        this.tradeCount = tradeCount;
        this.liquidatedCount = liquidatedCount;
    }

    public Long getId() { return id; }
    public Long getGameId() { return gameId; }
    public String getPlayerKey() { return playerKey; }
    public Long getUserId() { return userId; }
    public String getNickname() { return nickname; }
    public boolean isBot() { return Boolean.TRUE.equals(bot); }
    public Integer getFinalRank() { return finalRank; }
    public BigDecimal getFinalAsset() { return finalAsset; }
    public BigDecimal getReturnRate() { return returnRate; }
    public Integer getTradeCount() { return tradeCount; }
    public Integer getLiquidatedCount() { return liquidatedCount; }
}
