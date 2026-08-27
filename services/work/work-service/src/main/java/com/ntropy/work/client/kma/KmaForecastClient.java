package com.ntropy.work.client.kma;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ntropy.work.config.WeatherProperties;

/**
 * 기상청 단기예보(getVilageFcst) 호출 전담 클라이언트.
 * weather-local.properties의 service-key는 공공데이터포털에서 제공하는 "일반 인증키" 값을
 * (이미 URL 인코딩된 상태 그대로) 넣는다. serviceKey는 별도로 이어붙여서 UriComponentsBuilder의
 * 인코딩 대상에서 제외한다 — 그러지 않으면 이미 인코딩된 값이 한 번 더 인코딩되어(%25 이중 인코딩)
 * "등록되지 않은 서비스키" 에러가 난다.
 */
@Component
public class KmaForecastClient {

    private static final DateTimeFormatter BASE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int[] BASE_HOURS = {2, 5, 8, 11, 14, 17, 20, 23};
    private static final int PROVIDE_DELAY_MINUTES = 10; // 발표 후 실제 제공까지의 지연

    private final RestTemplate restTemplate;
    private final WeatherProperties properties;

    // 격자(nx, ny) 단위 캐시. 기상청 단기예보는 발표시각(baseDateTime) 단위로만 갱신되므로,
    // 같은 격자에 대해 발표시각이 그대로면 캐시를 그대로 쓰고, 발표시각이 바뀌면 갱신한다.
    private final Map<GridKey, CacheEntry> forecastCache = new ConcurrentHashMap<>();

    public KmaForecastClient(
            @Qualifier("weatherRestTemplate") RestTemplate restTemplate,
            WeatherProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    public List<KmaForecastItem> fetchForecastItems(int nx, int ny) {
        BaseDateTime baseDateTime = resolveBaseDateTime(LocalDateTime.now());

        GridKey gridKey = new GridKey(nx, ny);
        CacheEntry cached = forecastCache.get(gridKey);
        if (cached != null && cached.baseDateTime().equals(baseDateTime)) {
            return cached.items();
        }

        List<KmaForecastItem> items = fetchFromKma(nx, ny, baseDateTime);
        forecastCache.put(gridKey, new CacheEntry(baseDateTime, items));
        return items;
    }

    private List<KmaForecastItem> fetchFromKma(int nx, int ny, BaseDateTime baseDateTime) {
        String encodedQuery = UriComponentsBuilder.newInstance()
                .queryParam("dataType", "JSON")
                .queryParam("numOfRows", 1000)
                .queryParam("pageNo", 1)
                .queryParam("base_date", baseDateTime.date().format(BASE_DATE_FORMAT))
                .queryParam("base_time", String.format("%02d00", baseDateTime.hour()))
                .queryParam("nx", nx)
                .queryParam("ny", ny)
                .build()
                .encode()
                .toUri()
                .getRawQuery();

        URI uri = URI.create(properties.getBaseUrl() + "?serviceKey=" + properties.getServiceKey()
                + "&" + encodedQuery);

        KmaApiResponse response = restTemplate.getForObject(uri, KmaApiResponse.class);

        if (response == null || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getResponse().getBody().getItems().getItem();
    }

    /**
     * 단기예보 발표시각(02/05/08/11/14/17/20/23시) 중, 지금 시각 기준으로
     * 이미 발표되고 제공 지연(10분)까지 지난 가장 최근 시각을 찾는다.
     */
    private BaseDateTime resolveBaseDateTime(LocalDateTime now) {
        LocalDateTime adjusted = now.minusMinutes(PROVIDE_DELAY_MINUTES);
        LocalDate date = adjusted.toLocalDate();
        int hour = adjusted.getHour();

        for (int i = BASE_HOURS.length - 1; i >= 0; i--) {
            if (BASE_HOURS[i] <= hour) {
                return new BaseDateTime(date, BASE_HOURS[i]);
            }
        }
        return new BaseDateTime(date.minusDays(1), BASE_HOURS[BASE_HOURS.length - 1]);
    }

    private record BaseDateTime(LocalDate date, int hour) {
    }

    private record GridKey(int nx, int ny) {
    }

    private record CacheEntry(BaseDateTime baseDateTime, List<KmaForecastItem> items) {
    }
}

