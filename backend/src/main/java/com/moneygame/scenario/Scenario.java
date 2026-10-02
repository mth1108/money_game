package com.moneygame.scenario;

import com.moneygame.marketdata.Interval;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 한 판에 쓰는 「종목 + 기간 + 단위」 묶음. (CLAUDE.md §3 M3)
 *
 * @param start    KST. 첫 봉(0틱 시작가)의 ts
 * @param end      KST. 마지막 봉(240틱)의 ts
 * @param barCount 241 = 시작가 1봉 + 240틱
 * @param symbols  라벨 순 (A, B, C, D). 1 ~ 4 종목
 */
public record Scenario(long id,
                       String title,
                       Interval interval,
                       LocalDateTime start,
                       LocalDateTime end,
                       int barCount,
                       List<ScenarioSymbol> symbols) {

    /** 게임 중에는 label 만 보인다. code·name 은 결과 화면에서 공개한다. */
    public record ScenarioSymbol(String label, String code, String name) {
    }

    public List<String> labels() {
        return symbols.stream().map(ScenarioSymbol::label).toList();
    }
}
