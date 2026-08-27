package com.ntropy.diagnosis.client;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.api.client.DiagnosisCommandClient;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.dto.DiagnosisCalculationInput;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.port.account.FinancialPosition;
import com.ntropy.diagnosis.port.account.FinancialPositionPort;
import com.ntropy.diagnosis.port.account.MonthlyExpense;
import com.ntropy.diagnosis.port.account.MonthlyExpensePort;
import com.ntropy.diagnosis.port.work.MonthlyIncomeAnalysis;
import com.ntropy.diagnosis.port.work.IncomeAnalysisPort;
import com.ntropy.diagnosis.service.DiagnosisResultService;

/**
 * DiagnosisCommandClient의 diagnosis-service 내부 구현체입니다.
 *
 * <p>현재 월은 호출할 때마다 임시 스냅샷으로 다시 계산합니다. 완료된 과거월은 이미
 * 확정돼 있으면 조회 클라이언트를 전혀 호출하지 않고 즉시 종료하고, 아직 확정되지
 * 않았으면(최초 계산이든 임시 스냅샷이 남아 있든) 월말 기준 자산으로 계산과 동시에
 * 확정합니다. 소득·소비·자산 조회 중 하나라도 실패하면 그대로 전파해 upsert 자체를
 * 시도하지 않으므로, 기존 정상 스냅샷은 별도 로직 없이 보존됩니다.</p>
 */
@Component
public class LocalDiagnosisCommandClient implements DiagnosisCommandClient {

    private final DiagnosisResultService diagnosisResultService;
    private final IncomeAnalysisPort incomeAnalysisPort;
    private final MonthlyExpensePort monthlyExpensePort;
    private final FinancialPositionPort financialPositionPort;
    private final Clock clock;

    /** 운영 환경에서는 서비스 표준 시간대(Asia/Seoul)의 시스템 시계를 사용합니다. */
    @Autowired
    public LocalDiagnosisCommandClient(
            DiagnosisResultService diagnosisResultService,
            IncomeAnalysisPort incomeAnalysisPort,
            MonthlyExpensePort monthlyExpensePort,
            FinancialPositionPort financialPositionPort
    ) {
        this(
                diagnosisResultService,
                incomeAnalysisPort,
                monthlyExpensePort,
                financialPositionPort,
                Clock.system(ZoneId.of("Asia/Seoul"))
        );
    }

    /** 날짜 경계 테스트에서 고정 시계를 주입하기 위한 생성자입니다. */
    LocalDiagnosisCommandClient(
            DiagnosisResultService diagnosisResultService,
            IncomeAnalysisPort incomeAnalysisPort,
            MonthlyExpensePort monthlyExpensePort,
            FinancialPositionPort financialPositionPort,
            Clock clock
    ) {
        this.diagnosisResultService = diagnosisResultService;
        this.incomeAnalysisPort = incomeAnalysisPort;
        this.monthlyExpensePort = monthlyExpensePort;
        this.financialPositionPort = financialPositionPort;
        this.clock = clock;
    }

    @Override
    public void recalculate(Long userId, YearMonth yearMonth) {
        if (yearMonth == null) {
            throw new ServiceException(DiagnosisErrorCode.INVALID_REQUEST, "yearMonth는 필수입니다.");
        }

        YearMonth currentMonth = YearMonth.now(clock);

        if (yearMonth.isAfter(currentMonth)) {
            throw new ServiceException(DiagnosisErrorCode.INVALID_REQUEST, "미래 연월은 재계산할 수 없습니다.");
        }

        if (yearMonth.equals(currentMonth)) {
            recalculateCurrentMonth(userId, yearMonth);
            return;
        }

        recalculatePastMonth(userId, yearMonth);
    }

    private void recalculateCurrentMonth(Long userId, YearMonth yearMonth) {
        FinancialPosition financialPosition = financialPositionPort.findFinancialPosition(userId);
        DiagnosisCalculationInput input = buildInput(userId, yearMonth, financialPosition);
        diagnosisResultService.calculateAndUpsert(input, false);
    }

    private void recalculatePastMonth(Long userId, YearMonth yearMonth) {
        DiagnosisResult existing =
                diagnosisResultService.findByUserIdAndYearMonthOrNull(userId, yearMonth.toString());
        if (existing != null && existing.getFinalizedAt() != null) {
            // 이미 확정된 과거월이다. 조회 클라이언트를 호출하지 않고 조용히 종료한다.
            return;
        }

        LocalDate asOf = yearMonth.atEndOfMonth();
        FinancialPosition financialPosition = financialPositionPort.findFinancialPosition(userId, asOf);
        DiagnosisCalculationInput input = buildInput(userId, yearMonth, financialPosition);
        diagnosisResultService.calculateAndUpsert(input, true);
    }

    private DiagnosisCalculationInput buildInput(
            Long userId, YearMonth yearMonth, FinancialPosition financialPosition
    ) {
        if (financialPosition == null) {
            throw invalidCalculationSource("금융자산 조회 결과가 없습니다.");
        }

        MonthlyIncomeAnalysis incomeAnalysis =
                incomeAnalysisPort.getMonthlyIncomeAnalysis(userId, yearMonth);
        if (incomeAnalysis == null) {
            throw invalidCalculationSource("소득분석 조회 결과가 없습니다.");
        }

        MonthlyExpense monthlyExpense =
                monthlyExpensePort.findMonthlyExpense(userId, yearMonth.toString());
        if (monthlyExpense == null) {
            throw invalidCalculationSource("월별 소비 조회 결과가 없습니다.");
        }

        return new DiagnosisCalculationInput(
                userId,
                yearMonth.toString(),
                incomeAnalysis.totalIncome(),
                incomeAnalysis.unmatchedIncome(),
                monthlyExpense.totalExpense(),
                monthlyExpense.fixedExpense(),
                financialPosition.totalFinancialAssets(),
                financialPosition.liquidAssets(),
                financialPosition.safeAssets()
        );
    }

    private ServiceException invalidCalculationSource(String detail) {
        return new ServiceException(DiagnosisErrorCode.INVALID_CALCULATION_INPUT, detail);
    }
}
