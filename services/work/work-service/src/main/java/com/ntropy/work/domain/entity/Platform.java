package com.ntropy.work.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Platform {

    private Long platformId;
    private Long categoryId;
    private String platformName;
    private String depositName;
    private String settlementCycle;
    private String settlementTriggerType;
    private Integer settlementOffsetDay;
    private String settlementOffsetUnit;
    private String settlementDayOfWeek;
    private Integer settlementDayOfMonth;
}
