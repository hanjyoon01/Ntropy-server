package com.ntropy.work.client.kma;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 기상청 단기예보(getVilageFcst) item 하나. category별로 한 행씩 내려온다
 * (예: 같은 fcstDate+fcstTime에 SKY, PTY, TMP, POP이 각각 별도 item).
 */
@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class KmaForecastItem {

    private String category;
    private String fcstDate;
    private String fcstTime;
    private String fcstValue;
}
