package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * 시나리오 관리 커맨드. scenario 프로파일에서만 뜬다 (CLAUDE.md §2.3 과 같은 방식).
 * 게임 서버는 이 코드를 타지 않는다.
 *
 * 후보를 보여줄 뿐 자동으로 등록하지 않는다. 등록할 구간은 사람이 고른다 (§3 M3, 2026-10-02 결정).
 *
 * 실행 예 (backend/ 에서)
 *   후보   ./gradlew bootRun --args="--spring.profiles.active=scenario --action=candidates \
 *              --interval=1d --symbols=005930,000660,247540 --limit=10"
 *   등록   ./gradlew bootRun --args="--spring.profiles.active=scenario --action=register \
 *              --interval=1d --symbols=005930,000660,247540 --start=2024-03-04 --title=급등락장"
 *          분봉은 --start=2026-09-03T10:31
 *   목록   ./gradlew bootRun --args="--spring.profiles.active=scenario --action=list"
 *   확인   ./gradlew bootRun --args="--spring.profiles.active=scenario --action=show --id=1"
 */
@Component
@Profile("scenario")
public class ScenarioCommand implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScenarioCommand.class);

    private final ScenarioService service;

    public ScenarioCommand(ScenarioService service) {
        this.service = service;
    }

    @Override
    public void run(ApplicationArguments args) {
        String action = option(args, "action", "list");
        switch (action) {
            case "candidates" -> candidates(args);
            case "register" -> register(args);
            case "list" -> list();
            case "show" -> show(Long.parseLong(required(args, "id")));
            default -> throw new IllegalArgumentException("--action 은 candidates | register | list | show 중 하나입니다: " + action);
        }
    }

    private void candidates(ApplicationArguments args) {
        Interval interval = Interval.of(option(args, "interval", "1d"));
        List<String> codes = codes(args);
        int limit = Integer.parseInt(option(args, "limit", "10"));

        Map<String, List<Candle>> all = service.loadAll(codes, interval);
        List<ScenarioCandidateFinder.Candidate> found = ScenarioCandidateFinder.find(all, interval, limit);

        StringBuilder out = new StringBuilder("\n── 시나리오 후보 ").append(interval.code()).append(' ').append(codes)
                .append(" — 겹치지 않는 구간을 최소 이동폭 순으로 ").append(found.size()).append("개 ──\n");
        int no = 1;
        for (ScenarioCandidateFinder.Candidate c : found) {
            out.append(String.format("%2d. %s ~ %s  최소 이동폭 %s%n", no++, c.start(), c.end(),
                    ScenarioEligibility.percent(c.score())));
            c.stats().forEach((code, r) -> out.append(String.format("      %s  이동폭 %8s  최저 %8s  거래량0 %s%n",
                    code, ScenarioEligibility.percent(r.swing()), ScenarioEligibility.percent(r.drawdown()),
                    ScenarioEligibility.percent(r.zeroVolumeRatio()))));
        }
        if (found.isEmpty()) {
            out.append("  (적격 구간 없음)\n");
        }
        out.append("등록: --action=register --interval=").append(interval.code())
                .append(" --symbols=").append(String.join(",", codes)).append(" --start=<구간 시작> --title=<제목>");
        log.info(out.toString());
    }

    private void register(ApplicationArguments args) {
        Interval interval = Interval.of(required(args, "interval"));
        List<String> codes = codes(args);
        LocalDateTime start = parseStart(required(args, "start"));
        String title = required(args, "title");

        Scenario s = service.register(title, interval, codes, start, new SplittableRandom());
        log.info("\n── 등록 완료 ──\n{}", describe(s));
    }

    private void list() {
        List<Scenario> all = service.list();
        StringBuilder out = new StringBuilder("\n── 시나리오 ").append(all.size()).append("개 ──\n");
        for (Scenario s : all) {
            out.append(describe(s)).append('\n');
        }
        log.info(out.toString());
    }

    /** M3 완료 판정 확인용 — 시나리오 ID 로 라벨별 캔들과 뉴스를 읽어 요약한다. */
    private void show(long id) {
        LoadedScenario loaded = service.load(id);
        StringBuilder out = new StringBuilder("\n── 시나리오 적재 확인 ──\n").append(describe(loaded.scenario())).append('\n');
        loaded.candlesByLabel().forEach((label, candles) -> out.append(String.format(
                "  %s : %d봉  %s 종가 %s -> %s 종가 %s%n", label, candles.size(),
                candles.get(0).timestamp(), candles.get(0).close().toPlainString(),
                candles.get(candles.size() - 1).timestamp(), candles.get(candles.size() - 1).close().toPlainString())));
        out.append("  뉴스 ").append(loaded.newsByTick().values().stream().mapToInt(List::size).sum()).append("건");
        log.info(out.toString());
    }

    private static String describe(Scenario s) {
        List<String> symbols = new ArrayList<>();
        for (Scenario.ScenarioSymbol sym : s.symbols()) {
            symbols.add(sym.label() + "=" + sym.code() + "(" + sym.name() + ")");
        }
        return String.format("#%d %s [%s] %s ~ %s, %d봉, %s", s.id(), s.title(), s.interval().code(),
                s.start(), s.end(), s.barCount(), String.join(" ", symbols));
    }

    /** 날짜(2024-03-04)는 자정, 시각(2026-09-03T10:31)은 그대로 KST 로 본다. */
    private static LocalDateTime parseStart(String value) {
        return value.length() == 10 ? LocalDate.parse(value).atStartOfDay() : LocalDateTime.parse(value);
    }

    private static List<String> codes(ApplicationArguments args) {
        List<String> codes = new ArrayList<>();
        for (String part : required(args, "symbols").split(",")) {
            if (!part.isBlank()) {
                codes.add(part.trim());
            }
        }
        return codes;
    }

    private static String required(ApplicationArguments args, String name) {
        String value = option(args, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--" + name + " 이 필요합니다");
        }
        return value;
    }

    private static String option(ApplicationArguments args, String name, String defaultValue) {
        List<String> values = args.getOptionValues(name);
        return (values == null || values.isEmpty()) ? defaultValue : values.get(0);
    }
}
