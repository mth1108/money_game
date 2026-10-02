package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.moneygame.scenario.TestCandles.candle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M3 후보 추출. 등록하지 않고 보여주기만 한다 (2026-10-02 결정). */
@DisplayName("M3 시나리오 후보 추출")
class ScenarioCandidateFinderTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2024, 1, 1, 0, 0);

    /** 앞 300봉은 횡보, 뒤 300봉은 두 배로 오르는 일봉 600개 */
    private static List<Candle> flatThenRise(long base) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            list.add(candle(DAY.plusDays(i), Long.toString(base), "1000"));
        }
        for (int i = 0; i < 300; i++) {
            list.add(candle(DAY.plusDays(300 + i), Long.toString(base + base * i / 299), "1000"));
        }
        return list;
    }

    @Test
    void 모든_종목이_적격인_구간만_서로_겹치지_않게_고른다() {
        Map<String, List<Candle>> data = new LinkedHashMap<>();
        data.put("X", flatThenRise(10000));
        data.put("Y", flatThenRise(50000));

        List<ScenarioCandidateFinder.Candidate> found = ScenarioCandidateFinder.find(data, Interval.ONE_DAY, 10);

        assertFalse(found.isEmpty());
        // 횡보 구간만으로 된 창은 이동폭이 0 이라 탈락한다. 상승 구간을 품은 창만 남는다
        for (ScenarioCandidateFinder.Candidate c : found) {
            assertTrue(c.end().isAfter(DAY.plusDays(300)), "상승 구간을 포함해야 한다: " + c.start());
            assertEquals(2, c.stats().size());
        }
        // 600봉에서 241봉 창은 겹치지 않게 최대 2개다
        assertTrue(found.size() <= 2);
        for (int i = 1; i < found.size(); i++) {
            assertTrue(found.get(i).start().isAfter(found.get(i - 1).end())
                    || found.get(i).end().isBefore(found.get(i - 1).start()), "겹치면 안 된다");
            assertTrue(found.get(i - 1).score().compareTo(found.get(i).score()) >= 0, "이동폭 내림차순");
        }
    }

    @Test
    void 한_종목만_봉이_빠진_구간은_쓰지_않는다() {
        // Y 는 400번째 날이 거래정지로 빠졌다
        List<Candle> y = new ArrayList<>(flatThenRise(50000));
        LocalDateTime halted = y.remove(400).timestamp().toLocalDateTime();
        Map<String, List<Candle>> data = new LinkedHashMap<>();
        data.put("X", flatThenRise(10000));
        data.put("Y", y);

        List<ScenarioCandidateFinder.Candidate> found = ScenarioCandidateFinder.find(data, Interval.ONE_DAY, 10);

        for (ScenarioCandidateFinder.Candidate c : found) {
            assertTrue(halted.isBefore(c.start()) || halted.isAfter(c.end()),
                    "거래정지일을 품은 구간은 후보가 아니다: " + c.start() + " ~ " + c.end());
        }
    }

    @Test
    void 적격_구간이_없으면_빈_목록이다() {
        List<Candle> flat = new ArrayList<>();
        for (int i = 0; i < 500; i++) {
            flat.add(candle(DAY.plusDays(i), "10000", "1000"));
        }
        assertTrue(ScenarioCandidateFinder.find(Map.of("X", flat), Interval.ONE_DAY, 10).isEmpty());
    }

    @Test
    void 분봉_후보는_하루_안_정규장에서만_나온다() {
        // 이틀치 08:01 ~ 20:00 분봉. 매일 정규장 동안 5% 오른다
        List<Candle> list = new ArrayList<>();
        for (int d = 0; d < 2; d++) {
            LocalDateTime t = LocalDateTime.of(2026, 9, 3 + d, 8, 1);
            for (int i = 0; i < 720; i++) {
                LocalDateTime ts = t.plusMinutes(i);
                long close = 10000 + (ts.toLocalTime().isAfter(LocalTime.of(9, 0)) && ts.toLocalTime().isBefore(LocalTime.of(15, 21))
                        ? (ts.getHour() * 60 + ts.getMinute() - 541) * 2L : 0L);
                list.add(candle(ts, Long.toString(close), "100"));
            }
        }

        List<ScenarioCandidateFinder.Candidate> found = ScenarioCandidateFinder.find(Map.of("X", list), Interval.ONE_MINUTE, 10);

        assertFalse(found.isEmpty());
        for (ScenarioCandidateFinder.Candidate c : found) {
            assertEquals(c.start().toLocalDate(), c.end().toLocalDate(), "날짜를 넘기지 않는다");
            assertFalse(c.start().toLocalTime().isBefore(ScenarioRules.REGULAR_FIRST_BAR));
            assertFalse(c.end().toLocalTime().isAfter(ScenarioRules.REGULAR_LAST_BAR));
        }
    }

    @Test
    void 종목은_4개까지다() {
        Map<String, List<Candle>> five = new LinkedHashMap<>();
        for (String code : List.of("A1", "B1", "C1", "D1", "E1")) {
            five.put(code, flatThenRise(10000));
        }
        assertThrows(IllegalArgumentException.class, () -> ScenarioCandidateFinder.find(five, Interval.ONE_DAY, 10));
    }
}
