package com.moneygame.result.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 끝난 판 한 개. 종료 시 한 번 INSERT 하고 고치지 않는다 (2026-10-03 결정). DDL 원본은 db/schema.sql. */
@Entity
@Table(name = "game_rooms")
public class GameRoomEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_code", nullable = false, length = 16)
    private String roomCode;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    /** DAILY | MINUTE */
    @Column(name = "mode", nullable = false, length = 10)
    private String mode;

    @Column(name = "seed_money", nullable = false, precision = 24, scale = 8)
    private BigDecimal seedMoney;

    @Column(name = "total_ticks", nullable = false)
    private Integer totalTicks;

    /** KST. 실제로 쓴 첫 봉(0틱) */
    @Column(name = "period_start", nullable = false)
    private LocalDateTime periodStart;

    /** KST. 실제로 쓴 마지막 봉. 짧은 판이면 시나리오 끝보다 이르다 */
    @Column(name = "period_end", nullable = false)
    private LocalDateTime periodEnd;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at", nullable = false)
    private LocalDateTime finishedAt;

    protected GameRoomEntity() {
    }

    public GameRoomEntity(String roomCode, Long scenarioId, String mode, BigDecimal seedMoney, int totalTicks,
                          LocalDateTime periodStart, LocalDateTime periodEnd,
                          LocalDateTime startedAt, LocalDateTime finishedAt) {
        this.roomCode = roomCode;
        this.scenarioId = scenarioId;
        this.mode = mode;
        this.seedMoney = seedMoney;
        this.totalTicks = totalTicks;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
    }

    public Long getId() { return id; }
    public String getRoomCode() { return roomCode; }
    public Long getScenarioId() { return scenarioId; }
    public String getMode() { return mode; }
    public BigDecimal getSeedMoney() { return seedMoney; }
    public Integer getTotalTicks() { return totalTicks; }
    public LocalDateTime getPeriodStart() { return periodStart; }
    public LocalDateTime getPeriodEnd() { return periodEnd; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
}
