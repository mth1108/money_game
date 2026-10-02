package com.moneygame.scenario.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

/** scenario_symbols 복합 키 (scenario_id, label). */
@Embeddable
public class ScenarioSymbolId implements Serializable {

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;

    /** 'A' | 'B' | 'C' | 'D' */
    @Column(name = "label", nullable = false, length = 1)
    private String label;

    protected ScenarioSymbolId() {
    }

    public ScenarioSymbolId(Long scenarioId, String label) {
        this.scenarioId = scenarioId;
        this.label = label;
    }

    public Long getScenarioId() { return scenarioId; }
    public String getLabel() { return label; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ScenarioSymbolId other)) return false;
        return Objects.equals(scenarioId, other.scenarioId) && Objects.equals(label, other.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scenarioId, label);
    }
}
