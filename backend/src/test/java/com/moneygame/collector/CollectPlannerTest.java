package com.moneygame.collector;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M1 결손 구간 계산. (CLAUDE.md §3 M1 「이미 DB 에 있는 구간은 건너뛰고 결손 구간만 호출」) */
@DisplayName("M1 결손 구간 계산")
class CollectPlannerTest {

    private static final Duration DAY = Duration.ofDays(1);

    private static LocalDateTime d(String date) {
        return LocalDate.parse(date).atStartOfDay();
    }

    private static CollectPlanner.Range r(String from, String to) {
        return new CollectPlanner.Range(d(from), to == null ? null : d(to));
    }

    @Test
    void 받은_적이_없으면_요청_전체가_결손이다() {
        assertEquals(List.of(r("2024-01-01", null)),
                CollectPlanner.gaps(d("2024-01-01"), null, List.of(), DAY));
    }

    @Test
    void 중간만_받았으면_앞뒤_두_구간이고_최신_구간이_먼저다() {
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(d("2024-01-01"), null,
                List.of(r("2024-03-01", "2024-06-30")), DAY);

        assertEquals(List.of(r("2024-07-01", null), r("2024-01-01", "2024-02-29")), gaps);
    }

    @Test
    void 겹치거나_맞닿은_구간은_합쳐서_본다() {
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(d("2024-01-01"), d("2024-12-31"),
                List.of(r("2024-05-01", "2024-06-30"),
                        r("2024-03-01", "2024-05-15"),   // 겹침
                        r("2024-07-01", "2024-08-31")),  // 하루 차이로 맞닿음
                DAY);

        assertEquals(List.of(r("2024-09-01", "2024-12-31"), r("2024-01-01", "2024-02-29")), gaps);
    }

    @Test
    void 요청_범위를_다_받았으면_결손이_없다() {
        assertTrue(CollectPlanner.gaps(d("2024-03-01"), d("2024-04-30"),
                List.of(r("2024-01-01", "2024-12-31")), DAY).isEmpty());
    }

    @Test
    void 요청보다_이르거나_늦은_구간은_무시한다() {
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(d("2024-03-01"), d("2024-04-30"),
                List.of(r("2023-01-01", "2023-12-31"), r("2025-01-01", "2025-12-31")), DAY);

        assertEquals(List.of(r("2024-03-01", "2024-04-30")), gaps);
    }

    @Test
    void 요청_시작보다_앞에서_시작한_구간은_요청_안쪽만_뺀다() {
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(d("2024-03-01"), null,
                List.of(r("2024-01-01", "2024-05-31")), DAY);

        assertEquals(List.of(r("2024-06-01", null)), gaps);
    }

    @Test
    void 끝이_열린_구간이_있으면_그_뒤는_결손이_아니다() {
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(d("2024-01-01"), null,
                List.of(r("2024-06-01", null)), DAY);

        assertEquals(List.of(r("2024-01-01", "2024-05-31")), gaps);
    }

    @Test
    void 분봉은_1분_간격으로_맞닿음을_판단한다() {
        Duration minute = Duration.ofMinutes(1);
        LocalDateTime t = LocalDateTime.of(2026, 9, 3, 9, 0);
        List<CollectPlanner.Range> gaps = CollectPlanner.gaps(t, t.plusMinutes(60),
                List.of(new CollectPlanner.Range(t, t.plusMinutes(20)),
                        new CollectPlanner.Range(t.plusMinutes(21), t.plusMinutes(40))),
                minute);

        assertEquals(List.of(new CollectPlanner.Range(t.plusMinutes(41), t.plusMinutes(60))), gaps);
    }

    @Test
    void 끝이_시작보다_이른_구간은_만들_수_없다() {
        assertThrows(IllegalArgumentException.class, () -> r("2024-02-01", "2024-01-01"));
    }
}
