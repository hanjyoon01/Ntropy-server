package com.ntropy.work.api.client;

import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.api.dto.summary.MonthlyIncomeAnalysisSummary;

/** work-service가 diagnosis-service/ai-service에 제공하는 회원·연월별 소득분석 조회 계약. */
public interface IncomeAnalysisQueryClient {

    MonthlyIncomeAnalysisSummary getMonthlyIncomeAnalysis(
            Long userId,
            YearMonth yearMonth
    );

    /**
     * AI 리포트 배치 전용 벌크 조회. 여러 사용자의 소득분석을 한 번에 계산해 userId로
     * 매핑된 결과를 돌려준다. userIds에 없는 사용자는 결과에도 없다.
     *
     * <p>기본 구현은 getMonthlyIncomeAnalysis를 사용자마다 반복 호출하는 폴백이라
     * N+1 문제를 그대로 가진다 - 성능 이점을 실제로 얻으려면 구현체(work-service의
     * LocalIncomeAnalysisQueryClient)가 이 메서드를 반드시 오버라이드해야 한다.
     * default로 둔 이유는 이 인터페이스를 람다로 구현하는 기존 테스트 코드가
     * (추상 메서드가 2개가 되면 함수형 인터페이스가 깨져) 컴파일 실패하는 것을
     * 막기 위함이다.</p>
     */
    default Map<Long, MonthlyIncomeAnalysisSummary> getMonthlyIncomeAnalysisBulk(
            List<Long> userIds,
            YearMonth yearMonth
    ) {
        Map<Long, MonthlyIncomeAnalysisSummary> result = new LinkedHashMap<>();
        for (Long userId : userIds) {
            result.put(userId, getMonthlyIncomeAnalysis(userId, yearMonth));
        }
        return result;
    }
}
