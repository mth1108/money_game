package com.moneygame.scenario;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * 시나리오 규칙 상수. (CLAUDE.md §3 M3)
 *
 * 적격성 임계치는 아직 확정이 아니다. 플레이해보며 조정한다 (§8).
 */
public final class ScenarioRules {

    /** 한 판 길이. 방 설정으로 뺄지는 §8 미결정이다. */
    public static final int TICKS = 240;

    /** 시나리오 봉 수 = 시작가 1봉 + 240틱 (§3 M4 「시세와 틱의 관계」). */
    public static final int BARS = TICKS + 1;

    /** 게임 중 실제 종목명을 가리는 라벨. 시나리오 하나에 1 ~ 4 종목 (2026-10-02 결정). */
    public static final List<String> LABELS = List.of("A", "B", "C", "D");

    /** 구간 내 거래량 0 봉 비율 상한 */
    public static final BigDecimal MAX_ZERO_VOLUME_RATIO = new BigDecimal("0.05");

    /** 연속으로 멈춰도 되는 최대 봉 수. 1틱 = 1초라 그대로 화면 정지로 보인다 */
    public static final int MAX_CONSECUTIVE_ZERO_BARS = 5;

    /** 분봉: 시작가 대비 최대 이동폭 하한 */
    public static final BigDecimal MIN_SWING_1M = new BigDecimal("0.025");

    /** 일봉: 시작가 대비 최대 이동폭 하한 */
    public static final BigDecimal MIN_SWING_1D = new BigDecimal("0.30");

    /**
     * 분봉은 하루 안 정규장에서만 자른다. timestamp 가 봉 종료 시각이라
     * 09:00 봉(개장 전)과 15:21~15:30 봉(종가 단일가)을 뺀 09:01 ~ 15:20 이다 (§3 M3).
     */
    public static final LocalTime REGULAR_FIRST_BAR = LocalTime.of(9, 1);
    public static final LocalTime REGULAR_LAST_BAR = LocalTime.of(15, 20);

    private ScenarioRules() {
    }
}
