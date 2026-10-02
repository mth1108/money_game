package com.moneygame.scenario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** 시나리오 ↔ 종목. 게임 중에는 라벨만 보인다 (CLAUDE.md §3 M3). */
@Entity
@Table(name = "scenario_symbols")
public class ScenarioSymbolEntity {

    @EmbeddedId
    private ScenarioSymbolId id;

    @Column(name = "symbol_id", nullable = false)
    private Long symbolId;

    protected ScenarioSymbolEntity() {
    }

    public ScenarioSymbolEntity(Long scenarioId, String label, Long symbolId) {
        this.id = new ScenarioSymbolId(scenarioId, label);
        this.symbolId = symbolId;
    }

    public ScenarioSymbolId getId() { return id; }
    public Long getSymbolId() { return symbolId; }
}
