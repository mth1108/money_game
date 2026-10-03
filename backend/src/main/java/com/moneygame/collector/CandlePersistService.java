package com.moneygame.collector;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.CollectLogRepository;
import com.moneygame.marketdata.Interval;
import com.moneygame.marketdata.SymbolRepository;
import com.moneygame.marketdata.entity.CollectLogEntity;
import com.moneygame.marketdata.entity.SymbolEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * 수집 결과를 DB 에 적재한다. (CLAUDE.md §3 M1)
 *
 * 시세는 배치 insert, 수집 이력은 JPA 로 한 건 남긴다.
 * 한 종목의 적재 전체가 한 트랜잭션이라, 중간에 실패하면 이력도 남지 않는다.
 */
@Service
@Profile("collector")
public class CandlePersistService {

    private static final Logger log = LoggerFactory.getLogger(CandlePersistService.class);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final SymbolRepository symbols;
    private final CollectLogRepository collectLogs;
    private final PriceCandleBatchWriter batchWriter;

    public CandlePersistService(SymbolRepository symbols,
                                CollectLogRepository collectLogs,
                                PriceCandleBatchWriter batchWriter) {
        this.symbols = symbols;
        this.collectLogs = collectLogs;
        this.batchWriter = batchWriter;
    }

    @Transactional
    public int persist(String code, Interval interval, List<Candle> candles) {
        if (candles.isEmpty()) {
            return 0;
        }
        SymbolEntity symbol = symbols.findByCode(code).orElseGet(() -> {
            String market = isKrxCode(code) ? "KRX" : "US";
            log.warn("symbols 에 없는 종목이라 새로 넣습니다: {} (이름은 코드로 둡니다. 나중에 채우세요)", code);
            return symbols.save(new SymbolEntity(code, code, market));
        });

        // candles 는 시간 오름차순이다
        int written = batchWriter.write(symbol.getId(), interval, candles);
        collectLogs.save(new CollectLogEntity(
                symbol.getId(),
                interval.code(),
                toKst(candles.get(0).timestamp()),
                toKst(candles.get(candles.size() - 1).timestamp()),
                written,
                LocalDateTime.now(KST)));
        return written;
    }

    /** KRX 종목코드는 6자리 숫자다. 그 외는 미국 티커로 본다. */
    private static boolean isKrxCode(String code) {
        if (code.length() != 6) {
            return false;
        }
        for (int i = 0; i < code.length(); i++) {
            if (!Character.isDigit(code.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static LocalDateTime toKst(OffsetDateTime timestamp) {
        return timestamp.atZoneSameInstant(KST).toLocalDateTime();
    }
}
