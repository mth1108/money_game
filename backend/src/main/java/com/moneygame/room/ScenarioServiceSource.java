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
    private final SplittableRandom random = new SplittableRandom();

    public ScenarioServiceSource(ScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @Override
    public LoadedScenario load(GameMode mode, Long scenarioId) {
        Scenario scenario = scenarioId == null
                ? pick(mode)
                : scenarios.get(scenarioId);
        if (scenario.interval() != mode.interval()) {
            throw new IllegalArgumentException("시나리오 " + scenario.id() + " 는 " + scenario.interval().code()
                    + " 라 " + mode + " 방에서 쓸 수 없습니다");
        }
        return scenarios.load(scenario.id());
    }

    private Scenario pick(GameMode mode) {
        synchronized (random) {
            return scenarios.pickRandom(mode.interval(), random)
                    .orElseThrow(() -> new IllegalStateException(mode + " 모드로 등록된 시나리오가 없습니다"));
        }
    }
}
