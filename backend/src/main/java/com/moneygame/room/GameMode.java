package com.moneygame.room;

import com.moneygame.marketdata.Interval;

import java.util.Set;

/** 게임 모드와 허용 배율. (CLAUDE.md §3 M5 「배율 상한」) */
public enum GameMode {

    /** 일봉 — 배율 1, 2, 3 */
    DAILY(Interval.ONE_DAY, Set.of(1, 2, 3)),

    /** 분봉 — 배율 1, 3, 5, 10. 채택 여부는 §8 에서 열어 두었다 */
    MINUTE(Interval.ONE_MINUTE, Set.of(1, 3, 5, 10));

    private final Interval interval;
    private final Set<Integer> leverages;

    GameMode(Interval interval, Set<Integer> leverages) {
        this.interval = interval;
        this.leverages = leverages;
    }

    public Interval interval() { return interval; }
    public Set<Integer> leverages() { return leverages; }
}
