package com.moneygame.collector;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 시세 배치 insert. (CLAUDE.md §5 — 조회는 JPA, 적재는 배치)
 *
 * 한 종목의 1분봉 4년치가 77만 행이라 건건이 넣으면 끝나지 않는다.
 *
 * ON DUPLICATE KEY UPDATE 로 idempotent 하게 만든다. 같은 명령을 두 번 실행해도
 * 중복 행이 생기지 않는다 — M1 완료 판정이다.
 */
@Component
public class PriceCandleBatchWriter {

    /** DB 의 ts 는 KST 규약이다. DATETIME 이라 타임존을 담지 않는다. */
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final String SQL = """
            INSERT INTO price_candles
                (symbol_id, bar_interval, ts, open_price, high_price, low_price, close_price, volume)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) AS incoming
            ON DUPLICATE KEY UPDATE
                open_price  = incoming.open_price,
                high_price  = incoming.high_price,
                low_price   = incoming.low_price,
                close_price = incoming.close_price,
                volume      = incoming.volume
            """;

    private final JdbcTemplate jdbcTemplate;
    private final CollectorProperties properties;

    public PriceCandleBatchWriter(JdbcTemplate jdbcTemplate, CollectorProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    /** @return 실제로 넘긴 행 수 */
    public int write(long symbolId, Interval interval, List<Candle> candles) {
        int chunk = Math.max(1, properties.batchSize());
        int written = 0;
        for (int start = 0; start < candles.size(); start += chunk) {
            List<Candle> slice = candles.subList(start, Math.min(start + chunk, candles.size()));
            List<Object[]> args = new ArrayList<>(slice.size());
            for (Candle candle : slice) {
                args.add(new Object[]{
                        symbolId,
                        interval.code(),
                        Timestamp.valueOf(candle.timestamp().atZoneSameInstant(KST).toLocalDateTime()),
                        candle.open(), candle.high(), candle.low(), candle.close(), candle.volume()});
            }
            jdbcTemplate.batchUpdate(SQL, args);
            written += slice.size();
        }
        return written;
    }
}
