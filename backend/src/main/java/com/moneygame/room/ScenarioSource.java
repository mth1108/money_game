package com.moneygame.room;

import com.moneygame.scenario.LoadedScenario;

/**
 * 판 시작 시 시나리오를 읽어 오는 통로. 운영은 ScenarioService, 테스트는 가짜를 쓴다.
 * 판 시작 순간에만 불린다 — 틱 루프 중에는 I/O 를 하지 않는다 (§1.2).
 */
public interface ScenarioSource {

    /**
     * @param scenarioId null 이면 해당 모드의 시나리오 중 무작위
     * @param minBars    필요한 최소 봉 수 (= 판 길이 + 1). 무작위면 이만큼 있는 시나리오만 고르고,
     *                   지정한 시나리오가 이보다 짧으면 예외다
     */
    LoadedScenario load(GameMode mode, Long scenarioId, int minBars);
}
