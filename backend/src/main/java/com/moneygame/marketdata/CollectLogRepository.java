package com.moneygame.marketdata;

import com.moneygame.marketdata.entity.CollectLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CollectLogRepository extends JpaRepository<CollectLogEntity, Long> {

    List<CollectLogEntity> findBySymbolIdAndBarIntervalOrderByToTsDesc(Long symbolId, String barInterval);
}
