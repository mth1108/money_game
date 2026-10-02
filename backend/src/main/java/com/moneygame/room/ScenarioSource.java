package com.moneygame.room;

import com.moneygame.scenario.LoadedScenario;

/**
 * 판 시작 시 시나리오를 읽어 오는 통로. 운영은 ScenarioService, 테스트는 가짜를 쓴다.
 * 판 시작 순간에만 불린다 — 틱 루프 중에는 I/O 를 하지 않는다 (§1.2).
 */
public interface ScenarioSource {

    /** @param scenarioId null 이면 해당 모드의 시나리오 중 무작위 */
    LoadedScenario load(GameMode mode, Long scenarioId);
}
