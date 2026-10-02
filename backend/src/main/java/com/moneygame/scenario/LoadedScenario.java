package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 판 시작 시 한 번 읽어 둔 시나리오. (CLAUDE.md §3 M3 완료 판정 — 「시나리오 ID 를 주면
 * 캔들 배열 N개 + 이벤트 목록이 나온다」)
 *
 * 판이 도는 동안에는 이 값만 쓴다. DB·파일을 다시 읽지 않는다 (§1.2).
 *
 * @param candlesByLabel 라벨별 241봉. 모든 라벨의 i 번째 봉은 같은 시각이다
 * @param newsByTick     틱별 헤드라인. 지금은 비어 있다 (뉴스 생성 방식은 S6 에서 정한다)
 */
public record LoadedScenario(Scenario scenario,
                             Map<String, List<Candle>> candlesByLabel,
                             Map<Integer, List<String>> newsByTick) {

    /**
     * 엔진에 넘길 라벨별 종가 배열. GameSession 은 marketdata 를 모르므로
     * 캔들을 종가로 바꾸는 일은 여기서 한다 (§3 M4).
     */
    public Map<String, List<BigDecimal>> closeSeries() {
        Map<String, List<BigDecimal>> closes = new LinkedHashMap<>();
        candlesByLabel.forEach((label, candles) ->
                closes.put(label, candles.stream().map(Candle::close).toList()));
        return closes;
    }
}
