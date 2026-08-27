package com.ntropy.work.client.holiday;

import java.net.URI;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.ntropy.work.config.HolidayProperties;

/**
 * 공공데이터포털 한국천문연구원_특일 정보(getRestDeInfo) 호출 전담 클라이언트.
 * KmaForecastClient와 동일하게, holiday-local.properties의 service-key는 이미 URL 인코딩된
 * 값 그대로이므로 UriComponentsBuilder의 인코딩 대상에서 제외하고 별도로 이어붙인다
 * (그러지 않으면 이중 인코딩으로 "등록되지 않은 서비스키" 에러가 난다).
 */
@Component
public class HolidayApiClient {

    private static final int NUM_OF_ROWS = 100; // 한 해 공휴일(대체공휴일 포함)이 100건을 넘는 일은 없음

    private final RestTemplate restTemplate;
    private final HolidayProperties properties;

    public HolidayApiClient(
            @Qualifier("holidayRestTemplate") RestTemplate restTemplate,
            HolidayProperties properties
    ) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    /** 주어진 연도(solYear)의 공휴일 목록을 조회한다. */
    public List<HolidayApiItem> fetchHolidays(int year) {
        String encodedQuery = UriComponentsBuilder.newInstance()
                .queryParam("solYear", year)
                .queryParam("numOfRows", NUM_OF_ROWS)
                .queryParam("pageNo", 1)
                .queryParam("_type", "json")
                .build()
                .encode()
                .toUri()
                .getRawQuery();

        URI uri = URI.create(properties.getBaseUrl() + "?serviceKey=" + properties.getServiceKey()
                + "&" + encodedQuery);

        HolidayApiResponse response = restTemplate.getForObject(uri, HolidayApiResponse.class);

        if (response == null || response.getResponse() == null
                || response.getResponse().getBody() == null
                || response.getResponse().getBody().getItems() == null
                || response.getResponse().getBody().getItems().getItem() == null) {
            return Collections.emptyList();
        }
        return response.getResponse().getBody().getItems().getItem();
    }
}
