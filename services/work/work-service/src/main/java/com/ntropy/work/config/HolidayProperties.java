package com.ntropy.work.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class HolidayProperties {

    private final String serviceKey;
    private final String baseUrl;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public HolidayProperties(
            @Value("${holiday.service-key}") String serviceKey,
            @Value("${holiday.base-url}") String baseUrl,
            @Value("${holiday.connect-timeout-ms}") int connectTimeoutMillis,
            @Value("${holiday.read-timeout-ms}") int readTimeoutMillis
    ) {
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
