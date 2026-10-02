package com.moneygame.web;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * 금액·가격(BigDecimal)을 JSON 문자열로 내보낸다. (CLAUDE.md §1.5)
 *
 * 숫자로 내보내면 브라우저의 JavaScript 가 double 로 읽어 소수 끝자리가 깨진다
 * (예: 2196.66666667). 화면은 문자열을 그대로 보여주고, 계산은 서버만 한다.
 * 지수 표기(1E+8)를 피하려고 toPlainString 을 쓴다.
 */
@Configuration
public class JsonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer bigDecimalAsPlainString() {
        return builder -> builder.serializerByType(BigDecimal.class, new JsonSerializer<BigDecimal>() {
            @Override
            public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
                gen.writeString(value.toPlainString());
            }
        });
    }
}
