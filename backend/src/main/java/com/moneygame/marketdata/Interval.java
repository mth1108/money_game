package com.moneygame.marketdata;

/**
 * 봉 단위. 토스 API 가 제공하는 것은 이 둘뿐이다 (CLAUDE.md §3 M1 확정 API 사양).
 * 5분봉·시간봉은 API 에 없다.
 */
public enum Interval {

    ONE_MINUTE("1m"),
    ONE_DAY("1d");

    private final String code;

    Interval(String code) {
        this.code = code;
    }

    /** 토스 API 의 interval 쿼리 파라미터 값. */
    public String code() {
        return code;
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
