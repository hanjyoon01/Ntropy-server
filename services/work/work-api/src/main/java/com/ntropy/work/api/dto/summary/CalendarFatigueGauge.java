package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 7일 가중 피로도 게이지. 계산 수식(설계서 §3-2)이 아직 확정 전이라
 * CalendarService에서 당분간 null로 채운다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarFatigueGauge {

    private Integer score;
    private String level;
    private Boolean isOverThreshold;
}
