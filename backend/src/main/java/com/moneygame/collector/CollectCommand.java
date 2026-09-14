package com.moneygame.collector;

import com.moneygame.marketdata.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * 수집 커맨드. collector 프로파일에서만 뜬다 (CLAUDE.md §2.3).
 * 게임 서버는 기본 프로파일로 돌아 이 코드를 타지 않는다.
 *
 * 실행 예
 *   ./gradlew bootRun --args="--spring.profiles.active=collector \
 *       --symbols=005930,000660 --interval=1d --from=2015-01-01"
 *
 *   ./gradlew bootRun --args="--spring.profiles.active=collector \
 *       --symbols=005930 --interval=1m --max-bars=5000"
 */
@Component
@Profile("collector")
public class CollectCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(CollectCommand.class);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final CandleCollectService collectService;
    private final CsvCandleWriter csvWriter;
    private final CandlePersistService persistService;
    private final CollectorProperties properties;

    public CollectCommand(CandleCollectService collectService,
                          CsvCandleWriter csvWriter,
                          CandlePersistService persistService,
                          CollectorProperties properties) {
        this.collectService = collectService;
        this.csvWriter = csvWriter;
        this.persistService = persistService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        List<String> symbols = symbols(args);
        Interval interval = Interval.of(option(args, "interval", "1d"));
        OffsetDateTime from = parseFrom(option(args, "from", null));
        int maxBars = Integer.parseInt(option(args, "max-bars", "100000"));
        boolean adjusted = Boolean.parseBoolean(option(args, "adjusted", "true"));
        Path csvDir = Path.of(option(args, "csv-dir", properties.csvDir()));
        String target = option(args, "target", properties.target());
        boolean toCsv = target.contains("csv") || target.contains("both");
        boolean toDb = target.contains("db") || target.contains("both");
        if (!toCsv && !toDb) {
            throw new IllegalArgumentException("--target 은 db | csv | both 중 하나입니다: " + target);
        }

        log.info("수집 시작 — 종목 {}, 단위 {}, from {}, 상한 {}봉, 수정주가 {}, 적재 대상 {}",
                symbols, interval.code(), from, maxBars, adjusted, target);

        int totalBars = 0;
        int totalRows = 0;
        int failed = 0;
        for (String symbol : symbols) {
            try {
                CandleCollectService.CollectResult result =
                        collectService.collect(symbol, interval, from, maxBars, adjusted);
                Path file = toCsv ? csvWriter.write(symbol, interval, result.candles(), csvDir) : null;
                int rows = toDb ? persistService.persist(symbol, interval, result.candles()) : 0;
                totalBars += result.candles().size();
                totalRows += rows;
                log.info("""
                        
                        ── {} {} ──────────────────────────────
                          봉 수      : {}
                          구간       : {} ~ {}
                          요청 수    : {}
                          소요       : {}초
                          종료 사유  : {}
                          CSV        : {}
                          DB 적재    : {}행""",
                        symbol, interval.code(),
                        result.candles().size(),
                        result.oldest() == null ? "-" : result.oldest().timestamp(),
                        result.newest() == null ? "-" : result.newest().timestamp(),
                        result.requests(),
                        result.elapsed().toSeconds(),
                        result.stopReason(),
                        file == null ? "(생략)" : file.toAbsolutePath(),
                        toDb ? rows : "(생략)");
            } catch (RuntimeException e) {
                failed++;
                log.error("{} {} 수집 실패: {}", symbol, interval.code(), e.getMessage());
            }
        }
        log.info("수집 종료 — 종목 {}개, 총 {}봉, DB {}행, 실패 {}건",
                symbols.size(), totalBars, totalRows, failed);
    }

    private static List<String> symbols(ApplicationArguments args) {
        String raw = option(args, "symbols", null);
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("--symbols=005930,000660 형태로 종목을 지정하세요");
        }
        List<String> symbols = new ArrayList<>();
        for (String part : raw.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) symbols.add(trimmed);
        }
        return symbols;
    }

    /** 날짜(2024-01-01) 또는 ISO 오프셋 시각을 받는다. 날짜만 주면 KST 자정으로 본다. */
    private static OffsetDateTime parseFrom(String value) {
        if (value == null || value.isBlank()) return null;
        if (value.length() == 10) {
            return LocalDate.parse(value).atStartOfDay().atOffset(KST);
        }
        return OffsetDateTime.parse(value);
    }

    private static String option(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? defaultValue : values.get(0);
    }
}
