package com.ntropy.work.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class WeatherProperties {

    private final String serviceKey;
    private final String baseUrl;
    private final double defaultLatitude;
    private final double defaultLongitude;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public WeatherProperties(
            @Value("${weather.service-key}") String serviceKey,
            @Value("${weather.base-url}") String baseUrl,
            @Value("${weather.default-latitude}") double defaultLatitude,
            @Value("${weather.default-longitude}") double defaultLongitude,
            @Value("${weather.connect-timeout-ms}") int connectTimeoutMillis,
            @Value("${weather.read-timeout-ms}") int readTimeoutMillis
    ) {
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.defaultLatitude = defaultLatitude;
        this.defaultLongitude = defaultLongitude;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }
}