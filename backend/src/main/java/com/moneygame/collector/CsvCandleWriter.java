package com.moneygame.collector;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * 수집 결과를 CSV 로 덤프한다.
 *
 * 용도는 두 가지다.
 *   1) DB 없이 개발할 때의 시세 원본
 *   2) 통합 테스트와 밸런스 조정용 고정 데이터
 *      — 테스트가 실제 API 를 부르면 느리고, 허용 IP 없는 환경에선 실패한다.
 *        같은 구간을 반복해 돌려야 밸런스 비교가 된다.
 *
 * 파일명은 <symbol>_<interval>.csv, 컬럼은 토스 API 필드와 1:1 이다.
 * 행은 시간 오름차순으로 쓰지만, 읽는 쪽은 파일 순서를 믿지 말고 정렬해야 한다.
 */
@Component
public class CsvCandleWriter {

    public static final String HEADER = "timestamp,open,high,low,close,volume";

    public Path write(String symbol, Interval interval, List<Candle> candles, Path directory) throws IOException {
        Files.createDirectories(directory);
        Path file = directory.resolve(symbol + "_" + interval.code() + ".csv");
        try (BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Candle candle : candles) {
                writer.write(candle.timestamp().toString());
                writer.write(',');
                writer.write(candle.open().toPlainString());
                writer.write(',');
                writer.write(candle.high().toPlainString());
                writer.write(',');
                writer.write(candle.low().toPlainString());
                writer.write(',');
                writer.write(candle.close().toPlainString());
                writer.write(',');
                writer.write(candle.volume().toPlainString());
                writer.newLine();
            }
        }
        return file;
    }
}
