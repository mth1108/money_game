package com.moneygame.scenario;

import com.moneygame.scenario.entity.ScenarioSymbolEntity;
import com.moneygame.scenario.entity.ScenarioSymbolId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioSymbolRepository extends JpaRepository<ScenarioSymbolEntity, ScenarioSymbolId> {

    List<ScenarioSymbolEntity> findByIdScenarioIdOrderByIdLabelAsc(Long scenarioId);
}
