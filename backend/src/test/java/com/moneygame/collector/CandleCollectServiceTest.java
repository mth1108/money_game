package com.moneygame.collector;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M1 수집 — 페이지 넘기기와 결손 구간만 받기. 실제 API 대신 가짜 시세 출처를 쓴다.
 * 실제 토스 API 로의 확인은 API 를 쓸 수 있을 때 한다 (CLAUDE.md §9-3).
 */
@DisplayName("M1 캔들 수집")
class CandleCollectServiceTest {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);
    private static final CollectorProperties NO_DELAY = new CollectorProperties(0, 0, null, "db", 1000);

    /**
     * 토스 API 처럼 동작하는 가짜. 최신순으로 최대 count 개, before 는 포함,
     * nextBefore 는 이 페이지 마지막 봉의 다음(더 과거) 봉 — 없으면 null (CLAUDE.md §3 M1 확정 사양).
     */
    static final class FakeSource implements CandleSource {
        final List<Candle> newestFirst;
        final List<OffsetDateTime> requestedBefore = new ArrayList<>();

        FakeSource(List<Candle> candles) {
            this.newestFirst = new ArrayList<>(candles);
            this.newestFirst.sort(Comparator.comparing(Candle::timestamp).reversed());
        }

        @Override
        public CandlePage candles(String symbol, Interval interval, OffsetDateTime before, int count, boolean adjusted) {
            requestedBefore.add(before);
            List<Candle> eligible = newestFirst.stream()
                    .filter(c -> before == null || !c.timestamp().isAfter(before))
                    .toList();
            List<Candle> page = eligible.subList(0, Math.min(count, eligible.size()));
            OffsetDateTime next = eligible.size() > page.size() ? eligible.get(page.size()).timestamp() : null;
            return new CandlePage(List.copyOf(page), next);
        }
    }

    /** 하루 한 봉, start 부터 days 개 (주말 없이 단순하게) */
    private static List<Candle> daily(String start, int days) {
        List<Candle> list = new ArrayList<>();
        LocalDate d = LocalDate.parse(start);
        for (int i = 0; i < days; i++) {
            BigDecimal p = BigDecimal.valueOf(10000 + i);
            list.add(new Candle(d.plusDays(i).atStartOfDay().atOffset(KST), p, p, p, p, BigDecimal.ONE));
        }
        return list;
    }

    private static LocalDateTime day(String date) {
        return LocalDate.parse(date).atStartOfDay();
    }

    @Test
    void 전체_수집은_최근부터_from_까지_200봉씩_넘기며_중복_없이_오름차순으로_돌려준다() {
        FakeSource source = new FakeSource(daily("2023-01-01", 1000));   // 2023-01-01 ~ 2025-09-26
        CandleCollectService service = new CandleCollectService(source, NO_DELAY);

        CandleCollectService.CollectResult r = service.collect("X", Interval.ONE_DAY,
                day("2023-01-01").atOffset(KST), 100_000, true);

        assertEquals(1000, r.candles().size());
        assertEquals(5, r.requests(), "1000봉 / 200봉");
        assertEquals(day("2023-01-01"), r.oldest().timestamp().toLocalDateTime());
        assertEquals(1000, new TreeSet<>(r.candles().stream().map(Candle::timestamp).toList()).size(), "중복 없음");
        assertNull(source.requestedBefore.get(0), "가장 최근 봉부터");
    }

    @Test
    void 끝이_열린_결손_구간은_최근부터_구간_시작까지만_받는다() {
        FakeSource source = new FakeSource(daily("2023-01-01", 1000));
        CandleCollectService service = new CandleCollectService(source, NO_DELAY);

        CandleCollectService.CollectResult r = service.collectGap("X", Interval.ONE_DAY,
                new CollectPlanner.Range(day("2025-09-01"), null), 100_000, true);

        assertEquals(26, r.candles().size(), "2025-09-01 ~ 2025-09-26");
        assertEquals(1, r.requests());
        assertEquals(day("2025-09-01"), r.oldest().timestamp().toLocalDateTime(), "구간보다 이른 봉은 버린다");
    }

    @Test
    void 닫힌_결손_구간은_구간_끝에서_시작한다() {
        FakeSource source = new FakeSource(daily("2023-01-01", 1000));
        CandleCollectService service = new CandleCollectService(source, NO_DELAY);

        CandleCollectService.CollectResult r = service.collectGap("X", Interval.ONE_DAY,
                new CollectPlanner.Range(day("2023-01-01"), day("2023-03-01")), 100_000, true);

        assertEquals(60, r.candles().size(), "2023-01-01 ~ 2023-03-01");
        assertEquals(day("2023-03-01").atOffset(KST), source.requestedBefore.get(0), "before = 구간 끝 (포함)");
        assertEquals(day("2023-03-01"), r.newest().timestamp().toLocalDateTime());
    }

    @Test
    void 중간을_이미_받았으면_결손_구간만_받아_요청이_줄고_합치면_전체가_된다() {
        List<Candle> all = daily("2023-01-01", 1000);
        FakeSource source = new FakeSource(all);
        CandleCollectService service = new CandleCollectService(source, NO_DELAY);
        // 2023-03-01 ~ 2025-06-30 은 이미 받았다
        List<CollectPlanner.Range> covered = List.of(new CollectPlanner.Range(day("2023-03-01"), day("2025-06-30")));

        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(day("2023-01-01"), null, covered, Interval.ONE_DAY.step());
        TreeSet<OffsetDateTime> got = new TreeSet<>();
        int requests = 0;
        for (CollectPlanner.Range gap : gaps) {
            CandleCollectService.CollectResult r = service.collectGap("X", Interval.ONE_DAY, gap, 100_000, true);
            r.candles().forEach(c -> got.add(c.timestamp()));
            requests += r.requests();
        }

        assertEquals(2, gaps.size());
        assertEquals(2, requests, "전체를 받으면 5회 — 결손 구간만 받아 2회");
        // 이미 받은 구간 + 이번에 받은 구간 = 전체
        long coveredBars = all.stream().filter(c -> {
            LocalDateTime t = c.timestamp().toLocalDateTime();
            return !t.isBefore(day("2023-03-01")) && !t.isAfter(day("2025-06-30"));
        }).count();
        assertTrue(got.size() >= all.size() - coveredBars, "빠진 봉이 없다");
        assertTrue(got.stream().noneMatch(t -> t.toLocalDateTime().isAfter(day("2023-03-01"))
                && t.toLocalDateTime().isBefore(day("2025-06-30"))), "이미 받은 구간 안쪽은 다시 받지 않는다");
    }

    @Test
    void 조회_경계에_닿으면_멈춘다() {
        FakeSource source = new FakeSource(daily("2024-01-01", 50));
        CandleCollectService service = new CandleCollectService(source, NO_DELAY);

        CandleCollectService.CollectResult r = service.collect("X", Interval.ONE_DAY,
                day("2000-01-01").atOffset(KST), 100_000, true);

        assertEquals(50, r.candles().size());
        assertTrue(r.stopReason().startsWith("nextBefore = null"), r.stopReason());
    }
}
