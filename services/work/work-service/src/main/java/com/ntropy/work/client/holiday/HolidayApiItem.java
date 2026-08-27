package com.ntropy.work.client.holiday;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 특일 정보(getRestDeInfo) item 하나. locdate는 yyyyMMdd 형식의 숫자(예: 20260101)로 내려온다.
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class HolidayApiItem {

    private long locdate;
    private String dateName;
    private String isHoliday;
}
