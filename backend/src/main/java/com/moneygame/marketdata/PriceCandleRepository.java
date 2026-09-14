package com.moneygame.marketdata;

import com.moneygame.marketdata.entity.PriceCandleEntity;
import com.moneygame.marketdata.entity.PriceCandleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 시세 조회. 적재는 PriceCandleBatchWriter 가 배치로 한다 (CLAUDE.md §5).
 *
 * 조회는 항상 「한 종목의 특정 구간」이다. PK (symbol_id, bar_interval, ts) 를
 * 선두부터 그대로 타도록 조건을 건다.
 */
public interface PriceCandleRepository extends JpaRepository<PriceCandleEntity, PriceCandleId> {

    /** from, to 모두 포함. ts 는 KST 규약이다. 시간 오름차순으로 돌려준다. */
    @Query("""
            select c from PriceCandleEntity c
            where c.id.symbolId = :symbolId
              and c.id.barInterval = :barInterval
              and c.id.ts between :from and :to
            order by c.id.ts asc
            """)
    List<PriceCandleEntity> findRange(@Param("symbolId") Long symbolId,
                                      @Param("barInterval") String barInterval,
                                      @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    long countByIdSymbolIdAndIdBarInterval(Long symbolId, String barInterval);
}
