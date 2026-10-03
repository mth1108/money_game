package com.moneygame.collector;

import com.moneygame.marketdata.CollectLogRepository;
import com.moneygame.marketdata.Interval;
import com.moneygame.marketdata.SymbolRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 받은 구간 조회 — collect_logs 가 결손 구간 계산의 재료로 그대로 넘어오는지. 로컬 MySQL 필요.
 * 임시 종목·이력을 넣고 롤백한다.
 */
@Tag("integration")
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("M1 받은 구간 (MySQL)")
class CollectCoverageIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired SymbolRepository symbols;
    @Autowired CollectLogRepository collectLogs;

    private static LocalDateTime d(String date) {
        return LocalDate.parse(date).atStartOfDay();
    }

    @Test
    void 수집_이력이_받은_구간이_되고_결손_구간을_계산할_수_있다() {
        jdbc.update("INSERT INTO symbols (code, name, market) VALUES ('ZZCV', '테스트', 'KRX')");
        long id = jdbc.queryForObject("SELECT id FROM symbols WHERE code = 'ZZCV'", Long.class);
        String insert = "INSERT INTO collect_logs (symbol_id, bar_interval, from_ts, to_ts, bar_count, collected_at) "
                + "VALUES (?, ?, ?, ?, 10, NOW())";
        jdbc.update(insert, id, "1d", "2024-03-01", "2024-06-30");
        jdbc.update(insert, id, "1d", "2024-07-01", "2024-08-31");
        jdbc.update(insert, id, "1m", "2024-01-01 09:01", "2024-01-01 15:20");   // 다른 단위는 섞이지 않는다
        // 수집기 빈이지만 받은 구간 조회는 symbols·collect_logs 만 쓴다
        CandlePersistService persist = new CandlePersistService(symbols, collectLogs, null);

        List<CollectPlanner.Range> covered = persist.coverage("ZZCV", Interval.ONE_DAY);
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(d("2024-01-01"), null, covered, Interval.ONE_DAY.step());

        assertEquals(2, covered.size());
        assertEquals(List.of(new CollectPlanner.Range(d("2024-09-01"), null),
                new CollectPlanner.Range(d("2024-01-01"), d("2024-02-29"))), gaps);
    }

    @Test
    void 처음_보는_종목은_받은_구간이_없다() {
        CandlePersistService persist = new CandlePersistService(symbols, collectLogs, null);
        assertTrue(persist.coverage("ZZNEVER", Interval.ONE_DAY).isEmpty());
    }
}
