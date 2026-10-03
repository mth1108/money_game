package com.moneygame.realtime.message;

import com.moneygame.engine.GameResult;
import com.moneygame.engine.OrderResult;
import com.moneygame.room.Bar;
import com.moneygame.room.GameMode;
import com.moneygame.room.PlayerSnapshot;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 서버 → 클라이언트 메시지. 봉투는 {"type": "...", "payload": {...}} 이다. (CLAUDE.md §3 M7)
 * 금액·가격은 문자열로 나간다 (JsonConfig, §1.5).
 */
public record ServerMessage(Type type, Object payload) {

    public enum Type {
        /** 참가자 목록, 준비 상태 — 변경 시 */
        ROOM_STATE,
        /** 종목 라벨, 초기 캔들, 시드머니, 허용 배율 — 시작 시 */
        GAME_START,
        /** tickIndex, 종목별 현재가, 남은 시간 — 1초 */
        TICK,
        /** 내 현금·포지션·총자산 — 1초, 본인에게만 */
        PLAYER_STATE,
        /** 체결/거부 + 사유 — 주문 처리 후, 본인에게만 */
        ORDER_RESULT,
        /** 전원의 순위·총자산·보유 포지션·배율 — 5초 */
        RANKING,
        /** 누가 어느 종목에서 청산됐는지 — 발생 시, 전원 */
        LIQUIDATED,
        /** 헤드라인 — 발생 시, 전원 동시 */
        NEWS,
        /** 최종 순위, 결과 조회용 ID — 종료 시 */
        GAME_END,
        /** 요청을 처리하지 못했다 — 요청한 연결에만 */
        ERROR
    }

    public static ServerMessage of(Type type, Object payload) {
        return new ServerMessage(type, payload);
    }

    // ── payload ──

    /**
     * @param initialBars 0틱 캔들. 시각은 없다 — 라벨이 가린 종목을 날짜로 알아내지 못하게 한다
     */
    public record GameStart(GameMode mode, List<String> labels, Map<String, Bar> initialBars,
                            BigDecimal seedMoney, Set<Integer> leverages, int totalTicks, long tickMillis) {
    }

    public record Tick(int tickIndex, int totalTicks, int remainingTicks, long remainingMillis,
                       Map<String, BigDecimal> prices, Map<String, Bar> bars) {
    }

    public record Ranking(int tickIndex, List<RankingEntry> rankings) {
    }

    /** RANKING 에는 모든 참가자의 포지션과 배율이 들어간다 (의도된 설계, §3 M7). */
    public record RankingEntry(int rank, String userId, String nickname, BigDecimal totalAsset,
                               List<PlayerSnapshot.PositionView> positions, int liquidatedCount) {
    }

    public record Liquidated(int tickIndex, String userId, String nickname, String symbolLabel,
                             BigDecimal price, BigDecimal lostMargin, BigDecimal fee) {
    }

    public record News(int tickIndex, List<String> headlines) {
    }

    public record OrderResultPayload(String userId, OrderResult result) {
    }

    /**
     * @param resultId 결과 조회용 ID (game_rooms.id) — GET /api/results/{resultId}. 저장에 실패했으면 null
     * @param roomId   끝난 방 ID
     */
    public record GameEnd(Long resultId, String roomId, List<GameResult.Rank> rankings) {
    }

    public record Error(String message) {
    }
}
