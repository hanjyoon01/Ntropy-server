package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.api.dto.summary.WeatherForecast;
import com.ntropy.work.api.dto.summary.WeatherForecastList;
import com.ntropy.work.client.kma.KmaForecastClient;
import com.ntropy.work.client.kma.KmaForecastItem;
import com.ntropy.work.config.WeatherProperties;

class WeatherServiceTest {

    private static final double LATITUDE = 37.5665;
    private static final double LONGITUDE = 126.9780;

    private final WeatherProperties properties =
            new WeatherProperties("key", "url", LATITUDE, LONGITUDE, 5000, 10000);

    private WeatherService serviceReturning(List<KmaForecastItem> items) {
        return new WeatherService(new StubKmaForecastClient(items), properties);
    }

    private KmaForecastItem item(String category, String fcstDate, String fcstTime, String value) {
        KmaForecastItem item = new KmaForecastItem();
        item.setCategory(category);
        item.setFcstDate(fcstDate);
        item.setFcstTime(fcstTime);
        item.setFcstValue(value);
        return item;
    }

    @Test
    @DisplayName("SKY/PTY 코드는 한글 상태 문자열로 매핑된다")
    void getForecasts_mapsSkyAndPtyCodesToKorean() {
        List<KmaForecastItem> items = List.of(
                item("SKY", "20260803", "2000", "1"),
                item("PTY", "20260803", "2000", "0"),
                item("TMP", "20260803", "2000", "28")
        );

        WeatherForecast forecast = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE).getForecasts().get(0);

        assertEquals("맑음", forecast.getSkyStatus());
        assertEquals("없음", forecast.getPrecipitationType());
    }

    @Test
    @DisplayName("PTY가 0이 아니면 우천 할증 플래그가 true다")
    void getForecasts_ptyNotZero_setsRainSurchargeTrue() {
        List<KmaForecastItem> items = List.of(
                item("SKY", "20260803", "2000", "4"),
                item("PTY", "20260803", "2000", "1"),
                item("TMP", "20260803", "2000", "20")
        );

        WeatherForecast forecast = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE).getForecasts().get(0);

        assertTrue(forecast.isRainSurcharge());
    }

    @Test
    @DisplayName("PTY가 0이면 우천 할증 플래그가 false다")
    void getForecasts_ptyZero_setsRainSurchargeFalse() {
        List<KmaForecastItem> items = List.of(
                item("PTY", "20260803", "2000", "0")
        );

        WeatherForecast forecast = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE).getForecasts().get(0);

        assertTrue(!forecast.isRainSurcharge());
    }

    @Test
    @DisplayName("TMP 문자열은 반올림한 정수 온도로 변환된다")
    void getForecasts_roundsTemperatureValue() {
        List<KmaForecastItem> items = List.of(
                item("TMP", "20260803", "2000", "27.6")
        );

        WeatherForecast forecast = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE).getForecasts().get(0);

        assertEquals(28, forecast.getTemperature());
    }

    @Test
    @DisplayName("하루 대표값은 저녁(2000)에 가장 가까운 시각 데이터로 선택된다")
    void getForecasts_picksTimeNearestToEvening() {
        List<KmaForecastItem> items = List.of(
                item("TMP", "20260803", "1300", "20"),
                item("TMP", "20260803", "1900", "25")
        );

        WeatherForecast forecast = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE).getForecasts().get(0);

        assertEquals(25, forecast.getTemperature());
    }

    @Test
    @DisplayName("응답 일수가 5일을 넘어도 최대 5일치까지만 반환한다")
    void getForecasts_capsAtFiveDays() {
        List<KmaForecastItem> items = new ArrayList<>();
        for (int day = 3; day <= 9; day++) {
            items.add(item("TMP", String.format("202608%02d", day), "2000", "20"));
        }

        WeatherForecastList result = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE);

        assertEquals(5, result.getForecasts().size());
    }

    @Test
    @DisplayName("특정 카테고리가 누락되면 해당 필드만 null이다")
    void getForecasts_missingCategory_leavesOnlyThatFieldNull() {
        List<KmaForecastItem> items = List.of(
                item("TMP", "20260803", "2000", "20")
        );

        WeatherForecast forecast = serviceReturning(items).getForecasts(LATITUDE, LONGITUDE).getForecasts().get(0);

        assertNull(forecast.getSkyStatus());
        assertNull(forecast.getPrecipitationType());
        assertEquals(20, forecast.getTemperature());
        assertEquals(LocalDate.of(2026, 8, 3), forecast.getDate());
    }

    private static class StubKmaForecastClient extends KmaForecastClient {

        private final List<KmaForecastItem> items;

        StubKmaForecastClient(List<KmaForecastItem> items) {
            super(null, null);
            this.items = items;
        }

        @Override
        public List<KmaForecastItem> fetchForecastItems(int nx, int ny) {
            return items;
        }
    }
}
