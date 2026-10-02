package com.moneygame.scenario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** 시나리오. DDL 원본은 db/schema.sql 이다 (CLAUDE.md §5). */
@Entity
@Table(name = "scenarios")
public class ScenarioEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "title", nullable = false, length = 100)
    private String title;

    @Column(name = "bar_interval", nullable = false, length = 4)
    private String barInterval;

    /** KST. 첫 봉(0틱 시작가)의 ts */
    @Column(name = "start_ts", nullable = false)
    private LocalDateTime startTs;

    /** KST. 마지막 봉(240틱)의 ts */
    @Column(name = "end_ts", nullable = false)
    private LocalDateTime endTs;

    @Column(name = "bar_count", nullable = false)
    private Integer barCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ScenarioEntity() {
    }

    public ScenarioEntity(String title, String barInterval, LocalDateTime startTs, LocalDateTime endTs,
                          int barCount, LocalDateTime createdAt) {
        this.title = title;
        this.barInterval = barInterval;
        this.startTs = startTs;
        this.endTs = endTs;
        this.barCount = barCount;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getBarInterval() { return barInterval; }
    public LocalDateTime getStartTs() { return startTs; }
    public LocalDateTime getEndTs() { return endTs; }
    public Integer getBarCount() { return barCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
