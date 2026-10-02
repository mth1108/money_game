package com.moneygame.scenario;

import com.moneygame.scenario.entity.ScenarioEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScenarioRepository extends JpaRepository<ScenarioEntity, Long> {

    List<ScenarioEntity> findAllByOrderByIdAsc();

    List<ScenarioEntity> findByBarIntervalOrderByIdAsc(String barInterval);
}
