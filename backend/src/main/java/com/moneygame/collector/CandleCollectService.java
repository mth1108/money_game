package com.moneygame.collector;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 캔들 수집. nextBefore 를 따라 과거로 이동한다. (CLAUDE.md §3 M1)
 *
 * 반환은 항상 시간 오름차순이다. API 는 최신순으로 주므로 여기서 뒤집는다 (§3 M2).
 * timestamp 로 중복을 제거하므로 같은 명령을 두 번 실행해도 중복이 생기지 않는다.
 *
 * 수집 방식은 하나다 — 「before 에서 시작해 stopAt 까지 과거로 훑기」.
 *   전체 수집   : before = null(가장 최근),  stopAt = --from
 *   결손 구간   : before = 구간 끝,          stopAt = 구간 시작  (CollectPlanner 가 계산)
 */
@Service
@Profile("collector")
public class CandleCollectService {

    private static final Logger log = LoggerFactory.getLogger(CandleCollectService.class);
    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    private final CandleSource source;
    private final CollectorProperties properties;

    public CandleCollectService(CandleSource source, CollectorProperties properties) {
        this.source = source;
        this.properties = properties;
    }

    public record CollectResult(String symbol,
                                Interval interval,
                                List<Candle> candles,
                                int requests,
                                Duration elapsed,
                                String stopReason) {

        public Candle oldest() { return candles.isEmpty() ? null : candles.get(0); }
        public Candle newest() { return candles.isEmpty() ? null : candles.get(candles.size() - 1); }
    }

    /**
     * 가장 최근 봉부터 from 까지 (전체 수집).
     *
     * @param from     이 시각까지만 거슬러 올라간다. null 이면 maxBars 까지
     * @param maxBars  수집 상한. 무한 순회를 막는 안전장치다
     */
    public CollectResult collect(String symbol, Interval interval,
                                 OffsetDateTime from, int maxBars, boolean adjusted) {
        return collect(symbol, interval, null, from, maxBars, adjusted);
    }

    /** 결손 구간 하나만 (CollectPlanner 가 계산한 구간, KST). 끝이 열린 구간은 가장 최근 봉부터 훑는다. */
    public CollectResult collectGap(String symbol, Interval interval, CollectPlanner.Range gap,
                                    int maxBars, boolean adjusted) {
        OffsetDateTime before = gap.openEnded() ? null : gap.to().atOffset(KST);
        return collect(symbol, interval, before, gap.from().atOffset(KST), maxBars, adjusted);
    }

    /**
     * before(포함)에서 시작해 stopAt(포함)까지 과거로 훑는다. stopAt 보다 이른 봉은 버린다.
     *
     * @param before null 이면 가장 최근 봉부터
     * @param stopAt null 이면 maxBars 나 조회 경계까지
     */
    CollectResult collect(String symbol, Interval interval, OffsetDateTime before, OffsetDateTime stopAt,
                          int maxBars, boolean adjusted) {
        Instant started = Instant.now();
        Map<OffsetDateTime, Candle> collected = new LinkedHashMap<>();
        OffsetDateTime cursor = before;
        OffsetDateTime newestSeen = null;
        int requests = 0;
        String stopReason;

        while (true) {
            CandlePage page = source.candles(symbol, interval, cursor, CandleSource.MAX_COUNT, adjusted);
            requests++;

            if (page.candles().isEmpty()) {
                stopReason = "빈 응답 — 더 이상 과거 봉이 없습니다";
                break;
            }
            for (Candle candle : page.candles()) {
                collected.putIfAbsent(candle.timestamp(), candle);
            }

            // 응답은 최신순이므로 첫 원소가 가장 최근, 마지막 원소가 가장 오래된 봉이다
            if (newestSeen == null) {
                newestSeen = page.candles().get(0).timestamp();
            }
            Candle oldestInPage = page.candles().get(page.candles().size() - 1);

            if (requests % 10 == 0) {
                log.info("  {} {} — {}회 / {}봉 {}  현재 {}",
                        symbol, interval.code(), requests, collected.size(),
                        progress(started, collected.size(), maxBars, stopAt, newestSeen, oldestInPage.timestamp()),
                        oldestInPage.timestamp());
            }
            if (stopAt != null && !oldestInPage.timestamp().isAfter(stopAt)) {
                stopReason = "요청 구간 시작(" + stopAt + ")에 도달했습니다";
                break;
            }
            if (collected.size() >= maxBars) {
                stopReason = "수집 상한 " + maxBars + "봉에 도달했습니다";
                break;
            }
            if (page.nextBefore() == null) {
                stopReason = "nextBefore = null — 조회 가능 경계입니다";
                break;
            }
            cursor = page.nextBefore();
            sleep(properties.requestDelayMs());
        }

        List<Candle> ascending = new ArrayList<>(collected.values());
        if (stopAt != null) {
            ascending.removeIf(candle -> candle.timestamp().isBefore(stopAt));
        }
        ascending.sort(Comparator.comparing(Candle::timestamp));

        return new CollectResult(symbol, interval, List.copyOf(ascending), requests,
                Duration.between(started, Instant.now()), stopReason);
    }

    /**
     * 진행률과 예상 남은 시간.
     *
     * 3,000요청에 68분이 걸린 적이 있다. 시나리오를 여러 개 만들 때 수집 시간을
     * 계산에 넣어야 하므로 진행 상황을 눈으로 볼 수 있어야 한다.
     */
    private static String progress(Instant started, int collected, int maxBars,
                                   OffsetDateTime from, OffsetDateTime newest, OffsetDateTime oldest) {
        double byBars = maxBars > 0 ? (double) collected / maxBars : 0.0;
        double byTime = 0.0;
        if (from != null && newest != null) {
            long total = Duration.between(from, newest).toSeconds();
            long done = Duration.between(oldest, newest).toSeconds();
            if (total > 0) {
                byTime = (double) done / total;
            }
        }
        double ratio = Math.min(1.0, Math.max(byBars, byTime));
        if (ratio <= 0.0) {
            return "";
        }
        long elapsed = Duration.between(started, Instant.now()).toSeconds();
        long remaining = (long) (elapsed / ratio * (1 - ratio));
        return String.format("(%.0f%%, 남은 시간 약 %d분 %d초)", ratio * 100, remaining / 60, remaining % 60);
    }

    private static void sleep(long millis) {
        if (millis <= 0) return;
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("수집이 중단되었습니다", e);
        }
    }
}
