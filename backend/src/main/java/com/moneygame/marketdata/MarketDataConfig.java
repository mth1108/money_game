package com.moneygame.marketdata;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

/**
 * marketdata.provider 값으로 PriceDataProvider 구현체를 고른다.
 * 상위 코드는 인터페이스만 주입받으므로 구현체를 바꿔도 달라지지 않는다 (M2 완료 판정).
 */
@Configuration
public class MarketDataConfig {

    @Bean
    @ConditionalOnProperty(prefix = "marketdata", name = "provider", havingValue = "db")
    public PriceDataProvider dbPriceDataProvider(SymbolRepository symbols, PriceCandleRepository candles) {
        return new DbPriceDataProvider(symbols, candles);
    }

    @Bean
    @ConditionalOnProperty(prefix = "marketdata", name = "provider", havingValue = "csv")
    public PriceDataProvider csvPriceDataProvider(MarketDataProperties properties) {
        if (properties.csvDir() == null || properties.csvDir().isBlank()) {
            throw new IllegalStateException("marketdata.provider=csv 이면 marketdata.csv-dir 이 필요합니다");
        }
        return new CsvPriceDataProvider(Path.of(properties.csvDir()));
    }
}
