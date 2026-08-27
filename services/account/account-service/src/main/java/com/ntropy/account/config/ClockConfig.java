package com.ntropy.account.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 가상 데이터 생성과 일일 동기화처럼 "오늘"을 쓰는 로직을 서울 시간의 주입 가능한 Clock으로 제공한다. */
@Configuration
public class ClockConfig {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    @Bean
    public Clock clock() {
        return Clock.system(SERVICE_ZONE);
    }
}
