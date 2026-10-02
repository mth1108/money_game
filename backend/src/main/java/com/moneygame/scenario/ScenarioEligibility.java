package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 종목의 241봉 구간이 시나리오로 쓸 만한지 검사한다. (CLAUDE.md §3 M3 「적격성 검사」)
 *
 * 가격이 멈춘 구간에서는 청산도 수익도 나지 않아 화면이 얼어붙은 것처럼 보인다.
 * 변동이 작은 구간은 배율을 걸어도 밋밋하다. 둘 다 걸러낸다.
 *
 * 이동폭은 종가로 잰다 — 엔진이 매 틱 종가로 평가·청산하기 때문이다.
 *   이동폭 = max(최고 종가 / 시작 종가 − 1, 1 − 최저 종가 / 시작 종가)
 */
public final class ScenarioEligibility {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final int SCALE = 8;

    private ScenarioEligibility() {
    }

    /**
     * @param swing           시작 종가 대비 최대 이동폭 (상승·하락 중 큰 쪽)
     * @param drawdown        시작 종가 대비 최저 종가 (음수면 하락). 청산 가능성을 가늠하는 값이다
     * @param zeroVolumeRatio 거래량 0 봉 비율
     * @param reasons         탈락 사유. 비어 있으면 적격이다
     */
    public record Result(BigDecimal swing,
                         BigDecimal drawdown,
                         BigDecimal zeroVolumeRatio,
                         int maxConsecutiveZero,
                         List<String> reasons) {

        public boolean eligible() {
            return reasons.isEmpty();
        }
    }

    public static Result check(List<Candle> candles, Interval interval) {
        List<String> reasons = new ArrayList<>();
        if (candles.size() != ScenarioRules.BARS) {
            reasons.add("봉 수가 " + ScenarioRules.BARS + " 이 아닙니다: " + candles.size());
            if (candles.isEmpty()) {
                return new Result(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, List.copyOf(reasons));
            }
        }

        BigDecimal start = candles.get(0).close();
        BigDecimal max = start;
        BigDecimal min = start;
        int zero = 0;
        int run = 0;
        int maxRun = 0;
        for (Candle c : candles) {
            max = max.max(c.close());
            min = min.min(c.close());
            if (c.volume().signum() == 0) {
                zero++;
                run++;
                maxRun = Math.max(maxRun, run);
            } else {
                run = 0;
            }
        }

        BigDecimal up = max.divide(start, SCALE, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        BigDecimal drawdown = min.divide(start, SCALE, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
        BigDecimal swing = up.max(drawdown.negate());
        BigDecimal zeroRatio = BigDecimal.valueOf(zero)
                .divide(BigDecimal.valueOf(candles.size()), SCALE, RoundingMode.HALF_UP);

        BigDecimal minSwing = interval == Interval.ONE_MINUTE ? ScenarioRules.MIN_SWING_1M : ScenarioRules.MIN_SWING_1D;
        if (swing.compareTo(minSwing) < 0) {
            reasons.add("이동폭 " + percent(swing) + " < 하한 " + percent(minSwing));
        }
        if (zeroRatio.compareTo(ScenarioRules.MAX_ZERO_VOLUME_RATIO) > 0) {
            reasons.add("거래량 0 봉 비율 " + percent(zeroRatio) + " > 상한 " + percent(ScenarioRules.MAX_ZERO_VOLUME_RATIO));
        }
        if (maxRun > ScenarioRules.MAX_CONSECUTIVE_ZERO_BARS) {
            reasons.add("거래량 0 봉 연속 " + maxRun + "봉 > 상한 " + ScenarioRules.MAX_CONSECUTIVE_ZERO_BARS + "봉");
        }
        if (interval == Interval.ONE_MINUTE) {
            reasons.addAll(checkRegularSession(candles));
        }
        return new Result(swing, drawdown, zeroRatio, maxRun, List.copyOf(reasons));
    }

    /** 분봉은 하루 안, 09:01 ~ 15:20 에서만 자른다. 날짜를 넘기면 오버나이트 갭이 섞인다. */
    private static List<String> checkRegularSession(List<Candle> candles) {
        List<String> reasons = new ArrayList<>();
        LocalDateTime first = kst(candles.get(0));
        for (Candle c : candles) {
            LocalDateTime ts = kst(c);
            if (!ts.toLocalDate().equals(first.toLocalDate())) {
                reasons.add("날짜를 넘깁니다: " + first.toLocalDate() + " -> " + ts.toLocalDate());
                break;
            }
            LocalTime t = ts.toLocalTime();
            if (t.isBefore(ScenarioRules.REGULAR_FIRST_BAR) || t.isAfter(ScenarioRules.REGULAR_LAST_BAR)) {
                reasons.add("정규장(09:01 ~ 15:20) 밖의 봉이 있습니다: " + ts);
                break;
            }
        }
        return reasons;
    }

    static LocalDateTime kst(Candle c) {
        return c.timestamp().atZoneSameInstant(KST).toLocalDateTime();
    }

    static String percent(BigDecimal ratio) {
        return ratio.movePointRight(2).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
    }
}
