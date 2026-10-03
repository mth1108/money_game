package com.moneygame.room;

import com.moneygame.scenario.LoadedScenario;
import com.moneygame.scenario.Scenario;
import com.moneygame.scenario.ScenarioService;
import org.springframework.stereotype.Component;

import java.util.SplittableRandom;

/** ScenarioSource 운영 구현. M3 의 ScenarioService 로 읽는다. */
@Component
public class ScenarioServiceSource implements ScenarioSource {

    private final ScenarioService scenarios;
    private final RoomProperties properties;
    private final SplittableRandom random = new SplittableRandom();

    public ScenarioServiceSource(ScenarioService scenarios, RoomProperties properties) {
        this.scenarios = scenarios;
        this.properties = properties;
    }

    @Override
    public LoadedScenario load(GameMode mode, Long scenarioId, int minBars) {
        Scenario scenario = scenarioId == null
                ? pick(mode, minBars)
                : scenarios.get(scenarioId);
        if (scenario.interval() != mode.interval()) {
            throw new IllegalArgumentException("시나리오 " + scenario.id() + " 는 " + scenario.interval().code()
                    + " 라 " + mode + " 방에서 쓸 수 없습니다");
        }
        if (scenario.barCount() < minBars) {
            throw new IllegalStateException("시나리오 " + scenario.id() + " 는 " + scenario.barCount()
                    + "봉이라 " + (minBars - 1) + "틱 판을 돌 수 없습니다");
        }
        return scenarios.load(scenario.id(), properties.historyBars());
    }

    private Scenario pick(GameMode mode, int minBars) {
        synchronized (random) {
            return scenarios.pickRandom(mode.interval(), minBars, random)
                    .orElseThrow(() -> new IllegalStateException(mode + " 모드로 " + (minBars - 1)
                            + "틱 판을 돌 수 있는 시나리오가 없습니다"));
        }
    }
}
