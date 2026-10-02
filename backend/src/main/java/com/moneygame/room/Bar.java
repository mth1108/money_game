package com.moneygame.room;

import com.moneygame.marketdata.Candle;

import java.math.BigDecimal;

/**
 * 한 틱의 캔들. 시각은 뺀다 — 날짜와 가격을 같이 보내면 라벨이 가린 종목을 바로 알아낼 수 있다.
 * 차트(P1)용 재료다.
 */
public record Bar(BigDecimal open, BigDecimal high, BigDecimal low, BigDecimal close, BigDecimal volume) {

    public static Bar of(Candle c) {
        return new Bar(c.open(), c.high(), c.low(), c.close(), c.volume());
    }
}
