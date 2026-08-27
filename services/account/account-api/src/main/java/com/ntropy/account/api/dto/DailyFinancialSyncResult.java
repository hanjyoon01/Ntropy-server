package com.ntropy.account.api.dto;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

import com.ntropy.account.api.domain.DailyFinancialSyncProvider;

/**
 * 일일 금융거래 증분 동기화 한 번의 실행 결과 (이슈 #158). provider 단위로 반환되며,
 * 호출자는 {@code executionStatus}가 SUCCESS이거나 사용자가 {@code successfulUserIds}에
 * 포함된 경우만 소비 분류·diagnosis 재계산 대상으로 사용해야 한다.
 * {@code affectedYearMonthsByUser}는 "정확히 신규/변경된 거래"가 아니라 조회되어 멱등 upsert에
 * 성공한 거래가 속한 연월이다 — 재실행으로 이미 저장된 거래를 다시 upsert해도 포함된다.
 */
public record DailyFinancialSyncResult(
        LocalDate businessDate,
        DailyFinancialSyncProvider provider,
        String executionStatus,
        List<Long> successfulUserIds,
        Map<Long, List<YearMonth>> affectedYearMonthsByUser,
        List<Long> partialFailedUserIds,
        List<InstitutionSyncResult> institutionResults,
        long processedTransactionCount
) {

    /**
     * 기관별 처리 결과. 실패 사유는 계좌번호·생년월일·connectedId 등 민감정보를 포함하지 않는다.
     * 같은 기관 코드를 쓰는 여러 연결의 결과가 섞이지 않도록 내부 {@code codefConnectionId}를
     * 함께 반환한다.
     */
    public record InstitutionSyncResult(
            String organizationCode,
            Long codefConnectionId,
            String status,
            String errorCode
    ) {
    }
}
