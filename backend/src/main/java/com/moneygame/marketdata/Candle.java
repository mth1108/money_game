package com.moneygame.marketdata;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 캔들 하나. 토스 API 응답 필드와 1:1 로 맞춘다 — 변환이 없으면 버그도 없다.
 *
 * timestamp 의미가 단위마다 다르다 (CLAUDE.md §3 M1).
 *   1m : 봉 종료 시각. 구간은 [timestamp - 1분, timestamp)
 *   1d : 거래일 (시각은 현지 자정 고정)
 *
 * 가격은 API 가 decimal 문자열로 주므로 그대로 BigDecimal 로 받는다 (§1.5).
 */
public record Candle(OffsetDateTime timestamp,
                     BigDecimal open,
                     BigDecimal high,
                     BigDecimal low,
                     BigDecimal close,
                     BigDecimal volume) {
}
