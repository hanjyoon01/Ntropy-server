package com.ntropy.work.api.client;

import com.ntropy.work.api.dto.summary.WeatherForecastList;

/**
 * work-service의 날씨(기상청 단기예보) 조회 계약. work-service가 LocalWeatherQueryClient로
 * 구현하고, bff-service 등 다른 서비스는 이 인터페이스만 의존한다.
 */
public interface WeatherQueryClient {

    /**
     * latitude/longitude가 null이면 기본 좌표(서울시청)를 사용한다.
     */
    WeatherForecastList getForecasts(Double latitude, Double longitude);
}
