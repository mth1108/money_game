package com.moneygame.marketdata;

import java.time.Duration;

/**
 * 봉 단위. 토스 API 가 제공하는 것은 이 둘뿐이다 (CLAUDE.md §3 M1 확정 API 사양).
 * 5분봉·시간봉은 API 에 없다.
 */
public enum Interval {

    ONE_MINUTE("1m", Duration.ofMinutes(1)),
    ONE_DAY("1d", Duration.ofDays(1));

    private final String code;
    private final Duration step;

    Interval(String code, Duration step) {
        this.code = code;
        this.step = step;
    }

    /** 토스 API 의 interval 쿼리 파라미터 값. */
    public String code() {
        return code;
    }

    /** 이웃한 두 봉의 시각 차이. 받은 구간을 합칠 때 「맞닿았다」의 기준이다 (M1 결손 구간 계산). */
    public Duration step() {
        return step;
    }

    public static Interval of(String code) {
        for (Interval interval : values()) {
            if (interval.code.equals(code)) {
                return interval;
            }
        }
        throw new IllegalArgumentException("interval 은 1m 또는 1d 만 가능합니다: " + code);
    }
}
