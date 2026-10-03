package com.moneygame.collector;

import com.moneygame.marketdata.Interval;

import java.time.OffsetDateTime;

/**
 * 캔들 페이지를 가져오는 통로. 운영은 TossApiClient, 테스트는 가짜를 쓴다.
 * 다른 시세 출처를 붙일 때도 이 인터페이스만 구현하면 수집 로직(결손 구간 계산·페이지 넘기기)을 그대로 쓴다.
 *
 * §1.1 — 이 인터페이스는 시세 조회 하나만 담는다. 주문·계좌·잔고 메서드를 더하지 않는다.
 */
public interface CandleSource {

    /** 요청당 최대 봉 수 (토스 API 기준) */
    int MAX_COUNT = 200;

    /**
     * 최신순으로 최대 count 개.
     *
     * @param before 이 시각 이하(inclusive)의 봉부터. null 이면 가장 최근 봉부터
     */
    CandlePage candles(String symbol, Interval interval, OffsetDateTime before, int count, boolean adjusted);
}
