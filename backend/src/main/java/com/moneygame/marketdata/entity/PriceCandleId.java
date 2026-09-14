package com.moneygame.marketdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * price_candles 복합 키.
 *
 * 순서가 중요하다 — 조회는 항상 「한 종목의 특정 구간」이므로 symbol_id 가
 * 선두여야 인덱스를 탄다 (CLAUDE.md §5).
 */
@Embeddable
public class PriceCandleId implements Serializable {

    @Column(name = "symbol_id", nullable = false)
    private Long symbolId;

    /** '1m' | '1d'. interval 은 MySQL 예약어라 컬럼명을 bar_interval 로 둔다. */
    @Column(name = "bar_interval", nullable = false, length = 4)
    private String barInterval;

    /** KST. 1m 은 봉 종료 시각, 1d 는 거래일 자정. */
    @Column(name = "ts", nullable = false)
    private LocalDateTime ts;

    protected PriceCandleId() {
    }

    public PriceCandleId(Long symbolId, String barInterval, LocalDateTime ts) {
        this.symbolId = symbolId;
        this.barInterval = barInterval;
        this.ts = ts;
    }

    public Long getSymbolId() { return symbolId; }
    public String getBarInterval() { return barInterval; }
    public LocalDateTime getTs() { return ts; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PriceCandleId other)) return false;
        return Objects.equals(symbolId, other.symbolId)
                && Objects.equals(barInterval, other.barInterval)
                && Objects.equals(ts, other.ts);
    }

    @Override
    public int hashCode() {
        return Objects.hash(symbolId, barInterval, ts);
    }
}
