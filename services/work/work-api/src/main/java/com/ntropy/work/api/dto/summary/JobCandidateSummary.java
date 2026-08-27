package com.ntropy.work.api.dto.summary;

import java.math.BigDecimal;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온보딩 단계에서 입금 내역 매칭으로 산출된 잡 등록 후보를 다른 서비스/bff-service에
 * 노출하기 위한 공유 DTO.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobCandidateSummary {

    private Long categoryId;
    private String categoryName;
    private List<PlatformMatchSummary> platforms;
    private Integer settlementCount;
    private BigDecimal totalAmount;
}
