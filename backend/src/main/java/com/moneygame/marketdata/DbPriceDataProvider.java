package com.moneygame.marketdata;

import com.moneygame.marketdata.entity.PriceCandleEntity;
import com.moneygame.marketdata.entity.SymbolEntity;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;

/**
 * DB 에서 시세를 읽는다. 운영용 구현체다. (CLAUDE.md §3 M2)
 *
 * ⚠️ 미완 — 실데이터 검증 전이다 (CLAUDE.md §9).
 * 토스 API 를 쓸 수 없어 DB 에 시세가 없다. 쿼리·매핑은 통합 테스트에서 임시 행으로만
 * 검증했다. 그동안 게임 서버는 marketdata.provider=csv 로 CsvPriceDataProvider 를 쓴다.
 *
 * ts 는 KST 규약의 DATETIME 이다 (db/schema.sql). 돌려줄 때 +09:00 오프셋을 붙인다.
 */
public final class DbPriceDataProvider implements PriceDataProvider {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final SymbolRepository symbols;
    private final PriceCandleRepository candles;

    public DbPriceDataProvider(SymbolRepository symbols, PriceCandleRepository candles) {
        this.symbols = Objects.requireNonNull(symbols, "symbols");
        this.candles = Objects.requireNonNull(candles, "candles");
    }

    @Override
    public List<Candle> getCandles(String symbolCode, Interval interval,
                                   LocalDateTime from, LocalDateTime to) {
        Objects.requireNonNull(symbolCode, "symbolCode");
        Objects.requireNonNull(interval, "interval");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from 이 to 보다 늦습니다: " + from + " > " + to);
        }

        SymbolEntity symbol = symbols.findByCode(symbolCode)
                .orElseThrow(() -> new IllegalStateException("symbols 에 없는 종목입니다: " + symbolCode));

        // 정렬은 쿼리가 한다 (order by ts asc)
        return candles.findRange(symbol.getId(), interval.code(), from, to).stream()
                .map(DbPriceDataProvider::toCandle)
                .toList();
    }

    private static Candle toCandle(PriceCandleEntity e) {
        return new Candle(
                e.getId().getTs().atOffset(KST),
                e.getOpenPrice(),
                e.getHighPrice(),
                e.getLowPrice(),
                e.getClosePrice(),
                e.getVolume());
    }
}
