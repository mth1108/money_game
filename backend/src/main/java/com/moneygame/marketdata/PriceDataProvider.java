package com.moneygame.marketdata;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 게임 서버가 시세를 읽는 유일한 통로. (CLAUDE.md §3 M2 — 시그니처 고정)
 *
 * 구현체를 갈아 끼워도 상위 코드가 바뀌지 않아야 한다.
 *   DbPriceDataProvider  : 운영
 *   CsvPriceDataProvider : 초기 개발용
 *
 * 규약
 *   - from, to 는 KST 이고 양끝을 포함한다. DB 의 ts 규약과 같다 (db/schema.sql)
 *     1m : 봉 종료 시각 기준. 09:01 봉은 [09:00, 09:01) 구간이다
 *     1d : 거래일 자정
 *   - 반환은 항상 시간 오름차순이다. 토스 API 는 최신순으로 주므로 어딘가에서 뒤집어야 한다
 *   - 구간에 봉이 없으면 빈 목록이다. 종목 데이터 자체가 없으면 예외다
 */
public interface PriceDataProvider {

    List<Candle> getCandles(String symbolCode, Interval interval,
                            LocalDateTime from, LocalDateTime to);
}
