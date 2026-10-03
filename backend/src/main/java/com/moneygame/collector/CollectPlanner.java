package com.moneygame.collector;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 이미 받은 구간을 빼고 결손 구간만 계산한다. (CLAUDE.md §3 M1 「이미 DB 에 있는 구간은 건너뛰고 결손 구간만 호출」)
 *
 * 받은 구간은 collect_logs 의 [from_ts, to_ts] 다. 거래일이 아닌 날은 원래 봉이 없어서 price_candles 만으로는
 * 「안 받음」과 「받았는데 없음」을 구분할 수 없기 때문에 수집 이력을 쓴다.
 *
 * 순수 계산이다 — Spring·DB·API 를 모른다. 시각은 모두 KST LocalDateTime, 구간은 양끝 포함.
 */
public final class CollectPlanner {

    private CollectPlanner() {
    }

    /**
     * 양끝을 포함하는 구간.
     *
     * @param to null 이면 「지금(가장 최근 봉)까지」 — 끝이 열려 있다
     */
    public record Range(LocalDateTime from, LocalDateTime to) {

        public Range {
            if (from == null) {
                throw new IllegalArgumentException("from 이 필요합니다");
            }
            if (to != null && to.isBefore(from)) {
                throw new IllegalArgumentException("to 가 from 보다 이릅니다: " + from + " > " + to);
            }
        }

        public boolean openEnded() {
            return to == null;
        }
    }

    /**
     * 요청 범위 [from, to] 에서 받은 구간을 뺀 결손 구간들. **최신 구간이 먼저**다 — 수집은 최신에서 과거로 훑는다.
     *
     * @param from    요청 시작
     * @param to      요청 끝. null 이면 지금까지
     * @param covered 이미 받은 구간들 (순서·겹침 상관없음)
     * @param step    봉 간격. 끝과 시작이 이만큼 이내면 맞닿은 것으로 보고 합친다
     */
    public static List<Range> gaps(LocalDateTime from, LocalDateTime to, List<Range> covered, Duration step) {
        Range request = new Range(from, to);
        List<Range> merged = merge(covered, step);

        List<Range> gaps = new ArrayList<>();
        LocalDateTime cursor = request.from();
        for (Range block : merged) {
            if (block.to() != null && block.to().isBefore(cursor)) {
                continue;   // 요청보다 이른 구간
            }
            if (request.to() != null && block.from().isAfter(request.to())) {
                break;      // 요청보다 늦은 구간
            }
            if (block.from().isAfter(cursor)) {
                gaps.add(new Range(cursor, block.from().minus(step)));
            }
            if (block.to() == null) {
                cursor = null;   // 끝까지 받았다
                break;
            }
            cursor = later(cursor, block.to().plus(step));
        }
        if (cursor != null && (request.to() == null || !cursor.isAfter(request.to()))) {
            gaps.add(new Range(cursor, request.to()));
        }

        List<Range> newestFirst = new ArrayList<>(gaps);
        newestFirst.sort(Comparator.comparing(Range::from).reversed());
        return List.copyOf(newestFirst);
    }

    /** 겹치거나 step 이내로 맞닿은 구간을 합친다. 시작 순으로 정렬해 돌려준다. */
    static List<Range> merge(List<Range> ranges, Duration step) {
        List<Range> sorted = new ArrayList<>(ranges);
        sorted.sort(Comparator.comparing(Range::from));
        List<Range> merged = new ArrayList<>();
        for (Range r : sorted) {
            if (merged.isEmpty()) {
                merged.add(r);
                continue;
            }
            Range last = merged.get(merged.size() - 1);
            if (last.to() == null) {
                break;   // 끝이 열린 구간이 이미 나머지를 모두 덮는다
            }
            if (!r.from().isAfter(last.to().plus(step))) {
                LocalDateTime to = r.to() == null ? null : later(last.to(), r.to());
                merged.set(merged.size() - 1, new Range(last.from(), to));
            } else {
                merged.add(r);
            }
        }
        return merged;
    }

    private static LocalDateTime later(LocalDateTime a, LocalDateTime b) {
        return a.isAfter(b) ? a : b;
    }
}
