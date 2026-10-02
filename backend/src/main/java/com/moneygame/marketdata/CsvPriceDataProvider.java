package com.moneygame.marketdata;

import java.io.BufferedReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * CSV 파일에서 시세를 읽는다. 초기 개발용 구현체다. (CLAUDE.md §3 M2)
 *
 * 파일은 CsvCandleWriter 가 쓴 형식이다.
 *   <dir>/<symbol>_<interval>.csv
 *   timestamp,open,high,low,close,volume
 *
 * 파일 순서를 믿지 않고 읽은 뒤 정렬한다. Windows 에서 쓴 파일은 줄 끝이 CRLF 다.
 * 호출할 때마다 파일을 읽는다 — 판 시작 시 한 번만 불리므로 캐시하지 않는다 (§1.2).
 */
public final class CsvPriceDataProvider implements PriceDataProvider {

    /** CSV 의 timestamp 는 오프셋을 담지만, 구간 비교는 DB 와 같은 KST 규약으로 한다. */
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private static final String HEADER = "timestamp,open,high,low,close,volume";

    private final Path directory;

    public CsvPriceDataProvider(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
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

        Path file = directory.resolve(symbolCode + "_" + interval.code() + ".csv");
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("시세 파일이 없습니다: " + file.toAbsolutePath());
        }

        List<Candle> candles = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String header = reader.readLine();
            if (header == null || !HEADER.equals(header.strip())) {
                throw new IllegalStateException("CSV 헤더가 다릅니다: " + file + " — " + header);
            }
            String line;
            int lineNo = 1;
            while ((line = reader.readLine()) != null) {
                lineNo++;
                // BufferedReader 는 CRLF 를 줄 끝으로 처리한다. 앞뒤 공백만 걷어낸다
                String row = line.strip();
                if (row.isEmpty()) {
                    continue;
                }
                Candle candle = parse(row, file, lineNo);
                LocalDateTime ts = candle.timestamp().atZoneSameInstant(KST).toLocalDateTime();
                if (!ts.isBefore(from) && !ts.isAfter(to)) {
                    candles.add(candle);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("시세 파일을 읽지 못했습니다: " + file, e);
        }

        candles.sort(Comparator.comparing(Candle::timestamp));
        return List.copyOf(candles);
    }

    /** 가격은 문자열 그대로 BigDecimal 로 받는다 (§1.5). */
    private static Candle parse(String row, Path file, int lineNo) {
        String[] f = row.split(",", -1);
        if (f.length != 6) {
            throw new IllegalStateException("컬럼 수가 6 이 아닙니다: " + file + ":" + lineNo);
        }
        try {
            return new Candle(
                    OffsetDateTime.parse(f[0]),
                    new BigDecimal(f[1]),
                    new BigDecimal(f[2]),
                    new BigDecimal(f[3]),
                    new BigDecimal(f[4]),
                    new BigDecimal(f[5]));
        } catch (RuntimeException e) {
            throw new IllegalStateException("행을 해석하지 못했습니다: " + file + ":" + lineNo + " — " + row, e);
        }
    }
}
