package com.moneygame.room;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * 방 만들기 화면이 고를 수 있는 값. 화면이 선택지를 하드코딩하지 않게 서버가 내려 준다 —
 * 판 길이 목록이나 배율 상한이 바뀌어도 프론트를 고칠 필요가 없다.
 *
 * @param tickMillis 1틱의 실제 시간. 판 길이(틱) × 이 값 = 한 판의 시간
 */
public record RoomOptions(List<ModeOption> modes,
                          List<Integer> allowedTicks,
                          int defaultTicks,
                          long tickMillis,
                          int defaultMaxPlayers,
                          int maxPlayers,
                          int maxBots,
                          BigDecimal defaultSeedMoney) {

    /** @param leverages 그 모드에서 고를 수 있는 배율 (오름차순) */
    public record ModeOption(GameMode mode, List<Integer> leverages) {
    }

    public static RoomOptions of(RoomProperties properties) {
        List<ModeOption> modes = Arrays.stream(GameMode.values())
                .map(m -> new ModeOption(m, m.leverages().stream().sorted().toList()))
                .toList();
        return new RoomOptions(modes, properties.allowedTicks(), properties.defaultTicks(), properties.tickMillis(),
                RoomSettings.DEFAULT_MAX_PLAYERS, RoomSettings.MAX_PLAYERS, RoomSettings.MAX_BOTS,
                RoomSettings.DEFAULT_SEED_MONEY);
    }
}
