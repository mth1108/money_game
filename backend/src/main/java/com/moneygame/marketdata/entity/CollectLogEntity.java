package com.moneygame.marketdata.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 수집 이력.
 *
 * 「이 구간을 이미 받았나」를 price_candles 만으로는 판단할 수 없다.
 * 거래일이 아닌 날은 원래 데이터가 없어서 「안 받음」과 「받았는데 없음」이
 * 구분되지 않는다. 그 상태로는 68분짜리 수집을 중복으로 돌릴 위험이 있다.
 *
 * 배치 실행 이력이지 진행 중 상태가 아니므로 §1.3 에 해당하지 않는다.
 * 쓰기는 수집 배치가 끝나는 순간 한 번뿐이다.
 */
@Entity
@Table(name = "collect_logs")
public class CollectLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "symbol_id", nullable = false)
    private Long symbolId;

    @Column(name = "bar_interval", nullable = false, length = 4)
    private String barInterval;

    @Column(name = "from_ts", nullable = false)
    private LocalDateTime fromTs;

    @Column(name = "to_ts", nullable = false)
    private LocalDateTime toTs;

    @Column(name = "bar_count", nullable = false)
    private Integer barCount;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;

    protected CollectLogEntity() {
    }

    public CollectLogEntity(Long symbolId, String barInterval, LocalDateTime fromTs,
                            LocalDateTime toTs, int barCount, LocalDateTime collectedAt) {
        this.symbolId = symbolId;
        this.barInterval = barInterval;
        this.fromTs = fromTs;
        this.toTs = toTs;
        this.barCount = barCount;
        this.collectedAt = collectedAt;
    }

    public Long getId() { return id; }
    public Long getSymbolId() { return symbolId; }
    public String getBarInterval() { return barInterval; }
    public LocalDateTime getFromTs() { return fromTs; }
    public LocalDateTime getToTs() { return toTs; }
    public Integer getBarCount() { return barCount; }
    public LocalDateTime getCollectedAt() { return collectedAt; }
}
