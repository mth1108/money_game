package com.moneygame.marketdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 시세 구현체 선택. (CLAUDE.md §3 M2)
 *
 * @param provider db | csv. 운영 기본값은 db 다. 지금은 임시로 csv 를 쓴다 (CLAUDE.md §9)
 * @param csvDir   provider=csv 일 때 읽을 디렉터리. bootRun 의 작업 디렉터리는 backend/ 다
 */
@ConfigurationProperties(prefix = "marketdata")
public record MarketDataProperties(String provider, String csvDir) {
}
