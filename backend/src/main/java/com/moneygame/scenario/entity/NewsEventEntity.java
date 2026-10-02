package com.moneygame.scenario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 시나리오별 뉴스 이벤트. 지금은 비어 있다 — 생성 방식은 S6 에서 정한다 (2026-10-02 결정).
 */
@Entity
@Table(name = "news_events")
public class NewsEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    /** 발생 틱. 1 ~ 240 */
    @Column(name = "tick_index", nullable = false)
    private Integer tickIndex;

    @Column(name = "headline", nullable = false, length = 200)
    private String headline;

    protected NewsEventEntity() {
    }

    public NewsEventEntity(Long scenarioId, int tickIndex, String headline) {
        this.scenarioId = scenarioId;
        this.tickIndex = tickIndex;
        this.headline = headline;
    }

    public Long getId() { return id; }
    public Long getScenarioId() { return scenarioId; }
    public Integer getTickIndex() { return tickIndex; }
    public String getHeadline() { return headline; }
}
