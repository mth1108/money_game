package com.moneygame.marketdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 시세 캔들. 조회 전용으로 쓴다.
 * 적재는 건수가 많아 JdbcTemplate 배치 insert 로 한다 (CLAUDE.md §5).
 */
@Entity
@Table(name = "price_candles")
public class PriceCandleEntity {

    @EmbeddedId
    private PriceCandleId id;

    @Column(name = "open_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal openPrice;

    @Column(name = "high_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal highPrice;

    @Column(name = "low_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal lowPrice;

    @Column(name = "close_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal closePrice;

    @Column(name = "volume", nullable = false, precision = 20, scale = 0)
    private BigDecimal volume;

    protected PriceCandleEntity() {
    }

    public PriceCandleId getId() { return id; }
    public BigDecimal getOpenPrice() { return openPrice; }
    public BigDecimal getHighPrice() { return highPrice; }
    public BigDecimal getLowPrice() { return lowPrice; }
    public BigDecimal getClosePrice() { return closePrice; }
    public BigDecimal getVolume() { return volume; }
}
