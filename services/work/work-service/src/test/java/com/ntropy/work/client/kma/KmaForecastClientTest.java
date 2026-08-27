package com.ntropy.work.client.kma;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestTemplate;

import com.ntropy.work.config.WeatherProperties;

class KmaForecastClientTest {

    private static final String RESPONSE_JSON =
            "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"OK\"},"
                    + "\"body\":{\"items\":{\"item\":[]}}}}";

    @Test
    @DisplayName("같은 격자를 연속 조회하면 캐시를 재사용해 API를 한 번만 호출한다")
    void reusesCacheForSameGridWithinSameBaseTime() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        WeatherProperties properties = properties();

        server.expect(ExpectedCount.once(), requestToBaseUrl(properties))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        KmaForecastClient client = new KmaForecastClient(restTemplate, properties);

        List<KmaForecastItem> first = client.fetchForecastItems(60, 127);
        List<KmaForecastItem> second = client.fetchForecastItems(60, 127);

        assertSame(first, second, "같은 격자를 재조회하면 캐시된 동일 리스트 인스턴스를 반환해야 한다");
        server.verify();
    }

    @Test
    @DisplayName("격자가 다르면 캐시를 타지 않고 각각 API를 호출한다")
    void callsApiSeparatelyForDifferentGrids() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        WeatherProperties properties = properties();

        server.expect(ExpectedCount.times(2), requestToBaseUrl(properties))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(RESPONSE_JSON, MediaType.APPLICATION_JSON));

        KmaForecastClient client = new KmaForecastClient(restTemplate, properties);

        client.fetchForecastItems(60, 127);
        client.fetchForecastItems(61, 128);

        server.verify();
    }

    /** requestTo(Matcher)는 hamcrest가 필요해 대신 baseUrl 포함 여부만 확인하는 커스텀 매처를 쓴다. */
    private static RequestMatcher requestToBaseUrl(WeatherProperties properties) {
        return request -> assertTrue(
                request.getURI().toString().contains(properties.getBaseUrl()),
                "요청 URI에 baseUrl이 포함되어야 합니다: " + request.getURI()
        );
    }

    private static WeatherProperties properties() {
        return new WeatherProperties(
                "test-service-key",
                "https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst",
                37.5665, 126.9780,
                3000, 5000
        );
    }
}
