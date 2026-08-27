package com.ntropy.bff.dto.work.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.ntropy.work.api.dto.summary.JobCandidateSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 온보딩(02_잡 등록) 화면용 응답. 배달처럼 여러 플랫폼이 하나로 묶인 후보는
 * platforms에 여러 건이 담긴다. Meta 문구("카카오모빌리티 · 주 5회" 등) 조합과
 * 확인 상태 관리는 프론트/bff 화면 로직 책임이라 여기서는 원본 데이터만 내려준다.
 */
@Getter
@NoArgsConstructor
public class JobCandidateResponse {

    private Long categoryId;
    private String categoryName;
    private List<PlatformMatchResponse> platforms;
    private Integer settlementCount;
    private BigDecimal totalAmount;

    public static JobCandidateResponse from(JobCandidateSummary summary) {
        JobCandidateResponse response = new JobCandidateResponse();
        response.categoryId = summary.getCategoryId();
        response.categoryName = summary.getCategoryName();
        response.platforms = summary.getPlatforms().stream()
                .map(PlatformMatchResponse::from)
                .collect(Collectors.toList());
        response.settlementCount = summary.getSettlementCount();
        response.totalAmount = summary.getTotalAmount();
        return response;
    }
}
