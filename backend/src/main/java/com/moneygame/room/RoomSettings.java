package com.moneygame.room;

import java.math.BigDecimal;

/**
 * 방 설정. (CLAUDE.md §3 M6)
 *
 * @param scenarioId null 이면 판 시작 시 같은 모드의 시나리오 중 무작위로 고른다
 * @param seedMoney  기본 1억 원 (2026-10-02 결정)
 * @param bots       존버 봇 수 0 ~ 3 (2026-10-03 결정). 인원에 포함되고 항상 준비 상태다.
 *                   만든 사람 자리는 남아야 하므로 인원 − 1 을 넘을 수 없다
 */
public record RoomSettings(GameMode mode, int maxPlayers, Long scenarioId, BigDecimal seedMoney, int bots) {

    public static final int DEFAULT_MAX_PLAYERS = 4;
    public static final int MAX_PLAYERS = 8;
    public static final int MAX_BOTS = 3;
    public static final BigDecimal DEFAULT_SEED_MONEY = new BigDecimal("100000000");

    public RoomSettings {
        if (mode == null) {
            throw new IllegalArgumentException("모드를 정하세요 (DAILY | MINUTE)");
        }
        if (maxPlayers < 1 || maxPlayers > MAX_PLAYERS) {
            throw new IllegalArgumentException("인원은 1 ~ " + MAX_PLAYERS + "명입니다: " + maxPlayers);
        }
        if (seedMoney == null || seedMoney.signum() <= 0) {
            throw new IllegalArgumentException("시드머니는 0 보다 커야 합니다");
        }
        if (bots < 0 || bots > MAX_BOTS) {
            throw new IllegalArgumentException("봇은 0 ~ " + MAX_BOTS + "개입니다: " + bots);
        }
        if (bots > maxPlayers - 1) {
            throw new IllegalArgumentException("봇은 인원에 포함됩니다. 인원 " + maxPlayers + "명이면 봇은 "
                    + (maxPlayers - 1) + "개까지입니다");
        }
    }

    /** 봇 없는 방. */
    public RoomSettings(GameMode mode, int maxPlayers, Long scenarioId, BigDecimal seedMoney) {
        this(mode, maxPlayers, scenarioId, seedMoney, 0);
    }

    /** 빠진 값은 기본값으로 채운다. */
    public static RoomSettings of(GameMode mode, Integer maxPlayers, Long scenarioId, BigDecimal seedMoney, Integer bots) {
        return new RoomSettings(mode, maxPlayers == null ? DEFAULT_MAX_PLAYERS : maxPlayers,
                scenarioId, seedMoney == null ? DEFAULT_SEED_MONEY : seedMoney, bots == null ? 0 : bots);
    }
}
