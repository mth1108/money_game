package com.moneygame.scenario;

import com.moneygame.scenario.entity.NewsEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NewsEventRepository extends JpaRepository<NewsEventEntity, Long> {

    List<NewsEventEntity> findByScenarioIdOrderByTickIndexAscIdAsc(Long scenarioId);
}
