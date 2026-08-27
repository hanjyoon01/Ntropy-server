package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * deposit_name(입금 거래내역 대조용 내부 매칭 필드)은 계좌 매칭에만 쓰이는 내부 정보라 제외함.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformSummary {

    private Long platformId;
    private Long categoryId;
    private String platformName;
    private String settlementCycle;
    private Integer settlementOffsetDay;
    private String settlementDayOfWeek;
    private Integer settlementDayOfMonth;
}
