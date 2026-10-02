package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/** 시나리오 테스트용 캔들 생성·읽기·쓰기. */
final class TestCandles {

    static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private TestCandles() {
    }

    static Candle candle(LocalDateTime kst, String close, String volume) {
        BigDecimal c = new BigDecimal(close);
        return new Candle(kst.atOffset(KST), c, c, c, c, new BigDecimal(volume));
    }

    /**
     * 일봉 count 개. 첫날 start 부터 하루씩. 종가는 base 에서 시작해 마지막 봉에서 base x (1 + rise) 가 되도록
     * 정수로 선형 증가한다. 거래량은 모두 1000.
     */
    static List<Candle> daily(LocalDateTime start, int count, long base, double rise) {
        List<Candle> list = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long close = base + Math.round(base * rise * i / Math.max(1, count - 1));
            list.add(candle(start.plusDays(i), Long.toString(close), "1000"));
        }
        return list;
    }

    /** 픽스처 CSV 를 그대로 읽는다. */
    static List<Candle> fixture(String name) throws IOException {
        try (InputStream in = TestCandles.class.getResourceAsStream("/fixtures/" + name + ".csv")) {
            if (in == null) {
                throw new IllegalArgumentException("픽스처가 없습니다: " + name);
            }
            String[] lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n");
            List<Candle> list = new ArrayList<>();
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].strip();
                if (line.isEmpty()) continue;
                String[] f = line.split(",");
                list.add(new Candle(OffsetDateTime.parse(f[0]), new BigDecimal(f[1]), new BigDecimal(f[2]),
                        new BigDecimal(f[3]), new BigDecimal(f[4]), new BigDecimal(f[5])));
            }
            return list;
        }
    }

    /** CsvPriceDataProvider 가 읽는 형식(<code>_<interval>.csv)으로 쓴다. */
    static void writeCsv(Path dir, String code, String interval, List<Candle> candles) throws IOException {
        StringBuilder sb = new StringBuilder("timestamp,open,high,low,close,volume\n");
        for (Candle c : candles) {
            sb.append(c.timestamp()).append(',').append(c.open().toPlainString()).append(',')
                    .append(c.high().toPlainString()).append(',').append(c.low().toPlainString()).append(',')
                    .append(c.close().toPlainString()).append(',').append(c.volume().toPlainString()).append('\n');
        }
        Files.writeString(dir.resolve(code + "_" + interval + ".csv"), sb, StandardCharsets.UTF_8);
    }
}
