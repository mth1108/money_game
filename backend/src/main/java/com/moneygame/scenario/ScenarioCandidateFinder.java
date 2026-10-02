package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * 시나리오 후보 구간을 찾는다. (CLAUDE.md §3 M3, 2026-10-02 결정 「후보 추출 커맨드」)
 *
 * 자동으로 등록하지 않는다. 후보를 변동폭 순으로 보여줄 뿐이고, 등록은 사람이 고른다.
 *
 * 후보 조건
 *   - 모든 종목이 같은 241개 시각을 갖는다. 한 종목만 거래정지로 봉이 빠진 구간은 쓰지 않는다
 *   - 모든 종목이 적격성 검사를 통과한다 (ScenarioEligibility)
 * 정렬 기준은 종목별 이동폭 중 가장 작은 값이다 — 한 종목만 움직이는 판은 고를 게 없다.
 * 서로 겹치지 않는 구간만 고른다. 1봉씩 밀린 거의 같은 구간이 목록을 채우지 않게 한다.
 */
public final class ScenarioCandidateFinder {

    private ScenarioCandidateFinder() {
    }

    /**
     * @param score 종목별 이동폭 중 최솟값
     * @param stats 종목 코드별 적격성 결과
     */
    public record Candidate(LocalDateTime start,
                            LocalDateTime end,
                            BigDecimal score,
                            Map<String, ScenarioEligibility.Result> stats) {
    }

    /**
     * @param candlesBySymbol 종목 코드별 캔들 (시간 오름차순). 조회 가능한 전 구간을 넘긴다
     * @param limit           돌려줄 후보 수 상한
     */
    public static List<Candidate> find(Map<String, List<Candle>> candlesBySymbol, Interval interval, int limit) {
        if (candlesBySymbol.isEmpty() || candlesBySymbol.size() > ScenarioRules.LABELS.size()) {
            throw new IllegalArgumentException("종목은 1 ~ " + ScenarioRules.LABELS.size() + "개여야 합니다: "
                    + candlesBySymbol.size());
        }

        // 종목별 시각 -> 자기 시계열 안의 위치. 분봉은 정규장 봉만 남긴다
        Map<String, List<Candle>> series = new LinkedHashMap<>();
        Map<String, Map<LocalDateTime, Integer>> position = new HashMap<>();
        TreeSet<LocalDateTime> common = null;
        for (Map.Entry<String, List<Candle>> e : candlesBySymbol.entrySet()) {
            List<Candle> kept = new ArrayList<>();
            for (Candle c : e.getValue()) {
                if (interval == Interval.ONE_DAY || isRegular(ScenarioEligibility.kst(c).toLocalTime())) {
                    kept.add(c);
                }
            }
            Map<LocalDateTime, Integer> pos = new HashMap<>();
            for (int i = 0; i < kept.size(); i++) {
                pos.put(ScenarioEligibility.kst(kept.get(i)), i);
            }
            series.put(e.getKey(), kept);
            position.put(e.getKey(), pos);
            if (common == null) {
                common = new TreeSet<>(pos.keySet());
            } else {
                common.retainAll(pos.keySet());
            }
        }

        List<LocalDateTime> timeline = new ArrayList<>(common);
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i + ScenarioRules.BARS <= timeline.size(); i++) {
            LocalDateTime start = timeline.get(i);
            LocalDateTime end = timeline.get(i + ScenarioRules.BARS - 1);
            Candidate c = evaluate(series, position, interval, start, end);
            if (c != null) {
                candidates.add(c);
            }
        }

        candidates.sort(Comparator.comparing(Candidate::score).reversed()
                .thenComparing(Candidate::start));
        List<Candidate> picked = new ArrayList<>();
        for (Candidate c : candidates) {
            if (picked.size() >= limit) {
                break;
            }
            boolean overlaps = picked.stream()
                    .anyMatch(p -> !c.end().isBefore(p.start()) && !c.start().isAfter(p.end()));
            if (!overlaps) {
                picked.add(c);
            }
        }
        return List.copyOf(picked);
    }

    /** 모든 종목의 [start, end] 가 정확히 241봉이고 적격이면 후보, 아니면 null. */
    private static Candidate evaluate(Map<String, List<Candle>> series,
                                      Map<String, Map<LocalDateTime, Integer>> position,
                                      Interval interval, LocalDateTime start, LocalDateTime end) {
        Map<String, ScenarioEligibility.Result> stats = new LinkedHashMap<>();
        BigDecimal score = null;
        for (Map.Entry<String, List<Candle>> e : series.entrySet()) {
            int from = position.get(e.getKey()).get(start);
            int to = position.get(e.getKey()).get(end);
            if (to - from != ScenarioRules.BARS - 1) {
                return null;   // 이 종목은 구간 안에 다른 종목에 없는 봉이 있다
            }
            ScenarioEligibility.Result r = ScenarioEligibility.check(e.getValue().subList(from, to + 1), interval);
            if (!r.eligible()) {
                return null;
            }
            stats.put(e.getKey(), r);
            score = score == null ? r.swing() : score.min(r.swing());
        }
        return new Candidate(start, end, score, stats);
    }

    private static boolean isRegular(LocalTime t) {
        return !t.isBefore(ScenarioRules.REGULAR_FIRST_BAR) && !t.isAfter(ScenarioRules.REGULAR_LAST_BAR);
    }
}
