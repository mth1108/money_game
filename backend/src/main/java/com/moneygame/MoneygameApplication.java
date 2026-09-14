package com.moneygame;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 하나의 애플리케이션이지만 두 가지로 실행된다 (CLAUDE.md §2.3).
 *
 *   시세 수집 : --spring.profiles.active=collector  로 배치 실행 후 종료
 *   게임 서버 : 기본 프로파일로 상주
 *
 * 프로파일로 실행 경로를 나눠 게임 서버가 수집 코드를 타지 않게 한다.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class MoneygameApplication {

    public static void main(String[] args) {
        SpringApplication.run(MoneygameApplication.class, args);
    }
}
