package com.moneygame.scenario;

import com.moneygame.marketdata.Candle;
import com.moneygame.marketdata.Interval;
import com.moneygame.marketdata.PriceDataProvider;
import com.moneygame.marketdata.SymbolRepository;
import com.moneygame.marketdata.entity.SymbolEntity;
import com.moneygame.scenario.entity.NewsEventEntity;
import com.moneygame.scenario.entity.ScenarioEntity;
import com.moneygame.scenario.entity.ScenarioSymbolEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * 시나리오 조회 · 무작위 선택 · 판 시작 시 적재 · 등록. (CLAUDE.md §3 M3)
 *
 * 시세는 PriceDataProvider 로만 읽는다. 지금은 marketdata.provider=csv 다 (CLAUDE.md §9-1).
 */
@Service
public class ScenarioService {

    private static final ZoneOffset KST = ZoneOffset.ofHours(9);

    /** 「조회 가능한 전 구간」을 읽을 때 쓰는 경계. 일봉 최장 1975년부터다 (§3 M1). */
    private static final LocalDateTime BEGINNING = LocalDateTime.of(1970, 1, 1, 0, 0);
    private static final LocalDateTime FAR_FUTURE = LocalDateTime.of(2100, 1, 1, 0, 0);

    /** 분봉 정규장 하루 봉 수 (09:01 ~ 15:20). 과거 봉을 읽을 범위를 어림하는 데만 쓴다 */
    private static final int REGULAR_BARS_PER_DAY = 380;

    /** 과거 봉을 읽을 때 주말·연휴를 감안해 더 거슬러 올라가는 날 수 (추석 연휴 + 주말) */
    private static final int HISTORY_SLACK_DAYS = 14;

    private final ScenarioRepository scenarios;
    private final ScenarioSymbolRepository scenarioSymbols;
    private final NewsEventRepository newsEvents;
    private final SymbolRepository symbols;
    private final PriceDataProvider prices;

    public ScenarioService(ScenarioRepository scenarios,
                           ScenarioSymbolRepository scenarioSymbols,
                           NewsEventRepository newsEvents,
                           SymbolRepository symbols,
                           PriceDataProvider prices) {
        this.scenarios = scenarios;
        this.scenarioSymbols = scenarioSymbols;
        this.newsEvents = newsEvents;
        this.symbols = symbols;
        this.prices = prices;
    }

    @Transactional(readOnly = true)
    public List<Scenario> list() {
        return scenarios.findAllByOrderByIdAsc().stream().map(this::toScenario).toList();
    }

    @Transactional(readOnly = true)
    public Scenario get(long id) {
        return scenarios.findById(id).map(this::toScenario)
                .orElseThrow(() -> new IllegalArgumentException("시나리오가 없습니다: " + id));
    }

    /**
     * 해당 단위의 시나리오 중 하나를 고른다. 없으면 빈 값이다.
     *
     * @param minBars 필요한 최소 봉 수 (= 판 길이 + 1). 이보다 짧은 시나리오는 고르지 않는다
     */
    @Transactional(readOnly = true)
    public Optional<Scenario> pickRandom(Interval interval, int minBars, RandomGenerator random) {
        List<ScenarioEntity> pool = scenarios.findByBarIntervalOrderByIdAsc(interval.code()).stream()
                .filter(s -> s.getBarCount() >= minBars)
                .toList();
        if (pool.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toScenario(pool.get(random.nextInt(pool.size()))));
    }

    /** 과거 봉 없이 읽는다. */
    @Transactional(readOnly = true)
    public LoadedScenario load(long id) {
        return load(id, 0);
    }

    /**
     * 판 시작 시 한 번 부른다. 라벨별 241봉과 뉴스를 읽어 메모리에 올린다 (§1.2).
     * 봉 수나 시각이 어긋나면 판을 시작하지 않는다 — 틱마다 종목별 날짜가 달라진다.
     *
     * @param historyBars 라벨마다 함께 읽을 시작 전 과거 봉 수 (차트 배경). 0 이면 읽지 않는다
     */
    @Transactional(readOnly = true)
    public LoadedScenario load(long id, int historyBars) {
        Scenario scenario = get(id);
        Map<String, List<Candle>> candles = new LinkedHashMap<>();
        Map<String, List<Candle>> history = new LinkedHashMap<>();
        for (Scenario.ScenarioSymbol s : scenario.symbols()) {
            List<Candle> c = prices.getCandles(s.code(), scenario.interval(), scenario.start(), scenario.end());
            if (c.size() != scenario.barCount()) {
                throw new IllegalStateException("시나리오 " + id + " 의 " + s.label() + "(" + s.code() + ") 봉 수가 "
                        + scenario.barCount() + " 이 아닙니다: " + c.size());
            }
            candles.put(s.label(), c);
            history.put(s.label(), history(s.code(), scenario.interval(), scenario.start(), historyBars));
        }
        requireAligned(candles);

        Map<Integer, List<String>> news = new LinkedHashMap<>();
        for (NewsEventEntity e : newsEvents.findByScenarioIdOrderByTickIndexAscIdAsc(id)) {
            news.computeIfAbsent(e.getTickIndex(), k -> new ArrayList<>()).add(e.getHeadline());
        }
        news.replaceAll((tick, headlines) -> List.copyOf(headlines));
        return new LoadedScenario(scenario, Collections.unmodifiableMap(candles), Collections.unmodifiableMap(history),
                Collections.unmodifiableMap(news));
    }

    /**
     * 시작 시각 직전의 봉 count 개 (오래된 순). 데이터가 모자라면 있는 만큼만 돌려준다.
     * 분봉은 시나리오와 같은 정규장(09:01 ~ 15:20) 봉만 남긴다. 장 초반에 시작하면 전날 봉이 이어진다 —
     * 배경용이라 오버나이트 갭이 섞여도 괜찮다 (§8, 2026-10-03).
     */
    private List<Candle> history(String code, Interval interval, LocalDateTime start, int count) {
        if (count <= 0) {
            return List.of();
        }
        LocalDateTime from = interval == Interval.ONE_MINUTE
                ? start.minusDays(count / REGULAR_BARS_PER_DAY + HISTORY_SLACK_DAYS)
                : start.minusDays(count * 2L + HISTORY_SLACK_DAYS);
        List<Candle> before = prices.getCandles(code, interval, from, start.minusSeconds(1));
        if (interval == Interval.ONE_MINUTE) {
            before = before.stream().filter(c -> isRegularSession(kst(c.timestamp()).toLocalTime())).toList();
        }
        return List.copyOf(before.subList(Math.max(0, before.size() - count), before.size()));
    }

    /**
     * 시나리오가 쓴 실제 시세. 결과 화면 차트용이다 — 판이 끝난 뒤 조회할 때만 부른다 (§1.2).
     * 라벨 순서는 시나리오 순서다.
     */
    @Transactional(readOnly = true)
    public Map<String, List<Candle>> candles(Scenario scenario, LocalDateTime from, LocalDateTime to) {
        Map<String, List<Candle>> map = new LinkedHashMap<>();
        for (Scenario.ScenarioSymbol s : scenario.symbols()) {
            map.put(s.label(), prices.getCandles(s.code(), scenario.interval(), from, to));
        }
        return map;
    }

    private static boolean isRegularSession(LocalTime t) {
        return !t.isBefore(ScenarioRules.REGULAR_FIRST_BAR) && !t.isAfter(ScenarioRules.REGULAR_LAST_BAR);
    }

    /** 후보 추출용. 종목별로 조회 가능한 전 구간을 읽는다. */
    public Map<String, List<Candle>> loadAll(List<String> codes, Interval interval) {
        Map<String, List<Candle>> all = new LinkedHashMap<>();
        for (String code : codes) {
            all.put(code, prices.getCandles(code, interval, BEGINNING, FAR_FUTURE));
        }
        return all;
    }

    /**
     * 시나리오를 등록한다. start 부터 241봉을 잘라 적격성을 다시 확인한 뒤 넣는다.
     * 라벨은 무작위로 붙인다 — 매번 A 가 같은 종목이면 라벨이 실제 종목명을 가리지 못한다.
     */
    @Transactional
    public Scenario register(String title, Interval interval, List<String> codes,
                             LocalDateTime start, RandomGenerator random) {
        if (codes.isEmpty() || codes.size() > ScenarioRules.LABELS.size()) {
            throw new IllegalArgumentException("종목은 1 ~ " + ScenarioRules.LABELS.size() + "개여야 합니다: " + codes);
        }
        if (new HashSet<>(codes).size() != codes.size()) {
            throw new IllegalArgumentException("같은 종목이 두 번 있습니다: " + codes);
        }

        LocalDateTime bound = interval == Interval.ONE_MINUTE
                ? start.toLocalDate().atTime(ScenarioRules.REGULAR_LAST_BAR)
                : start.plusYears(3);
        Map<String, List<Candle>> windows = new LinkedHashMap<>();
        List<String> problems = new ArrayList<>();
        for (String code : codes) {
            if (symbols.findByCode(code).isEmpty()) {
                throw new IllegalArgumentException("symbols 에 없는 종목입니다: " + code);
            }
            List<Candle> c = prices.getCandles(code, interval, start, bound);
            List<Candle> window = c.subList(0, Math.min(ScenarioRules.BARS, c.size()));
            ScenarioEligibility.Result r = ScenarioEligibility.check(window, interval);
            if (!r.eligible()) {
                problems.add(code + ": " + String.join(", ", r.reasons()));
            }
            windows.put(code, window);
        }
        if (!problems.isEmpty()) {
            throw new IllegalArgumentException("적격성 검사를 통과하지 못했습니다 — " + String.join(" / ", problems));
        }
        requireAligned(windows);

        List<Candle> first = windows.values().iterator().next();
        if (!kst(first.get(0).timestamp()).equals(start)) {
            throw new IllegalArgumentException("start 에 봉이 없습니다: " + start
                    + " (가장 가까운 봉 " + kst(first.get(0).timestamp()) + ")");
        }
        LocalDateTime end = kst(first.get(first.size() - 1).timestamp());

        ScenarioEntity saved = scenarios.save(new ScenarioEntity(title, interval.code(), start, end,
                ScenarioRules.BARS, LocalDateTime.now(KST)));
        List<String> labels = new ArrayList<>(ScenarioRules.LABELS.subList(0, codes.size()));
        Collections.shuffle(labels, random);
        for (int i = 0; i < codes.size(); i++) {
            Long symbolId = symbols.findByCode(codes.get(i)).orElseThrow().getId();
            scenarioSymbols.save(new ScenarioSymbolEntity(saved.getId(), labels.get(i), symbolId));
        }
        scenarioSymbols.flush();
        return get(saved.getId());
    }

    /** 모든 종목의 i 번째 봉이 같은 시각이어야 한다. */
    private static void requireAligned(Map<String, List<Candle>> candles) {
        List<Candle> reference = null;
        String referenceKey = null;
        for (Map.Entry<String, List<Candle>> e : candles.entrySet()) {
            if (reference == null) {
                reference = e.getValue();
                referenceKey = e.getKey();
                continue;
            }
            if (e.getValue().size() != reference.size()) {
                throw new IllegalStateException("봉 수가 다릅니다: " + referenceKey + "=" + reference.size()
                        + ", " + e.getKey() + "=" + e.getValue().size());
            }
            for (int i = 0; i < reference.size(); i++) {
                if (!reference.get(i).timestamp().isEqual(e.getValue().get(i).timestamp())) {
                    throw new IllegalStateException(i + "번째 봉의 시각이 다릅니다: " + referenceKey + "="
                            + reference.get(i).timestamp() + ", " + e.getKey() + "=" + e.getValue().get(i).timestamp());
                }
            }
        }
    }

    private Scenario toScenario(ScenarioEntity e) {
        List<Scenario.ScenarioSymbol> list = new ArrayList<>();
        for (ScenarioSymbolEntity s : scenarioSymbols.findByIdScenarioIdOrderByIdLabelAsc(e.getId())) {
            SymbolEntity symbol = symbols.findById(s.getSymbolId())
                    .orElseThrow(() -> new IllegalStateException("symbols 에 없는 종목 id: " + s.getSymbolId()));
            list.add(new Scenario.ScenarioSymbol(s.getId().getLabel(), symbol.getCode(), symbol.getName()));
        }
        return new Scenario(e.getId(), e.getTitle(), Interval.of(e.getBarInterval()), e.getStartTs(), e.getEndTs(),
                e.getBarCount(), List.copyOf(list));
    }

    private static LocalDateTime kst(OffsetDateTime t) {
        return t.atZoneSameInstant(KST).toLocalDateTime();
    }
}
