package com.ntropy.diagnosis.client;

import org.springframework.stereotype.Component;

import com.ntropy.diagnosis.api.client.DiagnosisResultQueryClient;
import com.ntropy.diagnosis.api.dto.DiagnosisResultSummary;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.service.DiagnosisResultService;

import lombok.RequiredArgsConstructor;

/**
 * DiagnosisResultQueryClient의 diagnosis-service 내부 구현체입니다.
 *
 * bff는 DiagnosisResultQueryClient 인터페이스만 호출하고,
 * 실제 조회와 DTO 변환은 이 클래스가 담당합니다.
 */
@Component
@RequiredArgsConstructor
public class LocalDiagnosisResultQueryClient implements DiagnosisResultQueryClient {

    private final DiagnosisResultService diagnosisResultService;

    @Override
    public DiagnosisResultSummary findByUserIdAndYearMonth(Long userId, String yearMonth) {
        DiagnosisResult result = diagnosisResultService.findByUserIdAndYearMonth(userId, yearMonth);
        return toSummary(result);
    }

    private DiagnosisResultSummary toSummary(DiagnosisResult result) {
        return new DiagnosisResultSummary(
                result.getDiagnosisId(),
                result.getUserId(),
                result.getYearMonth(),
                result.getTotalIncome(),
                result.getUnmatchedIncome(),
                result.getTotalExpense(),
                result.getNetCashFlow(),
                result.getFixedExpense(),
                result.getFixedExpenseRatio(),
                result.getTotalFinancialAssets(),
                result.getLiquidAssets(),
                result.getSafeAssets(),
                result.getCalculatedAt()
        );
    }
}
