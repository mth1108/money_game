package com.moneygame.result;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** 결과·전적 조회. (CLAUDE.md §3 M8) */
@RestController
public class ResultController {

    private final ResultService results;

    public ResultController(ResultService results) {
        this.results = results;
    }

    /** 결과 상세 — GAME_END 의 resultId 로 부른다 */
    @GetMapping("/api/results/{gameId}")
    public ResultService.GameRecord result(@PathVariable long gameId) {
        return results.get(gameId);
    }

    /** 사용자별 전적 — 최근 판부터 */
    @GetMapping("/api/users/{userId}/results")
    public List<ResultService.HistoryEntry> history(@PathVariable long userId) {
        return results.history(userId);
    }
}
