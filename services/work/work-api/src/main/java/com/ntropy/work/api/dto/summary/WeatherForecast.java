package com.ntropy.work.api.dto.summary;

import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WeatherForecast {

    private LocalDate date;
    private String skyStatus;
    private String precipitationType;

    /** 강수형태가 "없음"이 아니면 true (배달/대리 등 우천 할증 판단용). */
    private boolean isRainSurcharge;

    private Integer temperature;
}
