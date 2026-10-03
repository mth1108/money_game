package com.moneygame.result;

import com.moneygame.result.entity.TradeEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    /** 시간 순. 같은 틱이면 체결 순서(저장 순)대로 */
    List<TradeEntity> findByGameIdOrderByTickIndexAscIdAsc(Long gameId);
}
