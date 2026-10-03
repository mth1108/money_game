package com.moneygame.collector;

import com.moneygame.marketdata.Candle;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 캔들 한 페이지. 최신순(내림차순)이다.
 *
 * nextBefore 는 이 페이지 마지막 봉의 *다음*(더 과거) 봉을 가리킨다. 그대로 넘기면
 * 중복 없이 이어진다. null 이면 마지막 페이지다 (CLAUDE.md §3 M1).
 */
public record CandlePage(List<Candle> candles, OffsetDateTime nextBefore) {

    public boolean isLast() {
        return nextBefore == null || candles.isEmpty();
    }
}
