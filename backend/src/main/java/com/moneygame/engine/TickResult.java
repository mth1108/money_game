package com.moneygame.engine;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 한 틱의 변경 사항. M6 가 받아 M7 로 넘긴다.
 */
public record TickResult(int tickIndex,
                         Map<String, BigDecimal> prices,
                         List<Liquidation> liquidations,
                         List<String> news,
                         boolean finished) {

    /** 누가 어느 종목에서 얼마를 잃고 청산됐는지. M7 의 LIQUIDATED 메시지 재료. */
    public record Liquidation(String userId,
                              String symbolLabel,
                              BigDecimal price,
                              BigDecimal lostMargin,
                              BigDecimal fee) {
    }
}
