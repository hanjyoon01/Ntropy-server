package com.ntropy.diagnosis.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.diagnosis.domain.entity.DiagnosisResult;
import com.ntropy.diagnosis.dto.DiagnosisCalculationInput;
import com.ntropy.diagnosis.exception.DiagnosisErrorCode;
import com.ntropy.diagnosis.mapper.DiagnosisResultMapper;

/**
 * 월별 재무 진단 계산과 저장·조회를 담당하는 Service입니다.
 */
@Service
public class DiagnosisResultService {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final DiagnosisResultMapper diagnosisResultMapper;
    private final Clock clock;

    /** 운영 환경에서는 서비스 표준 시간대(Asia/Seoul)의 시스템 시계를 사용합니다. */
    @Autowired
    public DiagnosisResultService(DiagnosisResultMapper diagnosisResultMapper) {
        this(diagnosisResultMapper, Clock.system(SERVICE_ZONE));
    }

    /** 확정 시각 테스트에서 고정 시계를 주입하기 위한 생성자입니다. */
    DiagnosisResultService(DiagnosisResultMapper diagnosisResultMapper, Clock clock) {
        this.diagnosisResultMapper = diagnosisResultMapper;
        this.clock = clock;
    }

    /**
     * 입력값을 검증하고 월별 진단 결과를 계산하여 저장합니다.
     *
     * 계산 규칙:
     * - netCashFlow = totalIncome - totalExpense
     * - fixedExpenseRatio = fixedExpense / totalIncome
     * - totalIncome이 0이면 fixedExpenseRatio는 null
     *
     * @param input 진단 계산 입력값
     * @param finalize true면 이번 호출로 확정한다(finalized_at 설정). 이미 확정된
     *                 (user_id, year_month) 행은 upsert SQL의 조건부 SET절이 어떤
     *                 값도 바꾸지 않도록 보호한다 - 호출 전에 확정 여부를 걸러내는 건
     *                 호출자(LocalDiagnosisCommandClient)의 책임이다.
     * @return 계산된 진단 결과
     */
    @Transactional
    public DiagnosisResult calculateAndUpsert(
            DiagnosisCalculationInput input,
            boolean finalize
    ) {
        validate(input);

        // 총소득에서 총소비를 차감하여 순현금흐름을 계산합니다. Long 범위를 넘으면 실패시킵니다.
        long netCashFlow;
        try {
            netCashFlow = Math.subtractExact(input.getTotalIncome(), input.getTotalExpense());
        } catch (ArithmeticException e) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "순현금흐름 계산 결과가 허용 범위를 초과했습니다."
            );
        }

        BigDecimal fixedExpenseRatio = null;

        // 총소득이 0보다 클 때만 고정지출 비율을 계산합니다.
        if (input.getTotalIncome() > 0) {
            fixedExpenseRatio = BigDecimal.valueOf(
                            input.getFixedExpense()
                    )
                    .divide(
                            BigDecimal.valueOf(input.getTotalIncome()),
                            4,
                            RoundingMode.HALF_UP
                    );
        }

        // 계산 결과를 DIAGNOSIS_RESULT 도메인 객체로 생성합니다.
        // calculatedAt/createdAt/updatedAt은 DB의 CURRENT_TIMESTAMP가 채웁니다.
        // finalizedAt은 서비스 표준 시간대 Clock으로 만들어 DB와 동일한 KST 의미를 유지합니다.
        DiagnosisResult diagnosisResult =
                new DiagnosisResult(
                        null,
                        input.getUserId(),
                        input.getYearMonth(),
                        input.getTotalIncome(),
                        input.getUnmatchedIncome(),
                        input.getTotalExpense(),
                        netCashFlow,
                        input.getFixedExpense(),
                        fixedExpenseRatio,
                        input.getTotalFinancialAssets(),
                        input.getLiquidAssets(),
                        input.getSafeAssets(),
                        null,
                        finalize ? LocalDateTime.now(clock) : null,
                        null,
                        null
                );

        // (user_id, year_month) 기준으로 신규 저장 또는 갱신합니다.
        diagnosisResultMapper.upsert(diagnosisResult);

        return diagnosisResult;
    }

    /**
     * 사용자·연월 기준으로 진단 결과를 조회합니다.
     *
     * userId·yearMonth를 검증하고, 해당 진단 결과가 없으면
     * DIAGNOSIS_RESULT_NOT_FOUND 예외를 발생시킵니다.
     * 읽기 전용 트랜잭션으로 실행합니다.
     */
    @Transactional(readOnly = true)
    public DiagnosisResult findByUserIdAndYearMonth(
            Long userId,
            String yearMonth
    ) {
        validateUserId(userId);
        validateYearMonthForQuery(yearMonth);

        DiagnosisResult result = diagnosisResultMapper.findByUserIdAndYearMonth(
                userId,
                yearMonth
        );

        if (result == null) {
            throw new ServiceException(DiagnosisErrorCode.DIAGNOSIS_RESULT_NOT_FOUND);
        }

        return result;
    }

    /**
     * 사용자·연월 기준으로 진단 결과를 조회합니다. 결과가 없으면 예외 없이 null을 반환합니다.
     *
     * 완료된 과거월 재계산 시 이미 확정됐는지 먼저 확인하는 용도로 사용합니다 -
     * 존재하지 않는 것은 오류가 아니라 "아직 계산된 적 없음"이라는 정상 상태입니다.
     */
    @Transactional(readOnly = true)
    public DiagnosisResult findByUserIdAndYearMonthOrNull(
            Long userId,
            String yearMonth
    ) {
        validateUserId(userId);
        validateYearMonthForQuery(yearMonth);

        return diagnosisResultMapper.findByUserIdAndYearMonth(userId, yearMonth);
    }

    /**
     * 사용자 기준으로 연월이 최신인 진단 결과부터 최대 limit건을 조회합니다.
     *
     * 결과가 없으면 빈 목록을 반환합니다 (예외를 발생시키지 않습니다).
     * 읽기 전용 트랜잭션으로 실행합니다.
     */
    @Transactional(readOnly = true)
    public List<DiagnosisResult> findLatestByUserId(
            Long userId,
            int limit
    ) {
        validateUserId(userId);
        if (limit <= 0) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "limit은 양수여야 합니다."
            );
        }

        return diagnosisResultMapper.findLatestByUserId(userId, limit);
    }

    /**
     * 조회 계약에서 공통으로 사용하는 userId 검증입니다.
     */
    private void validateUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "userId는 양수여야 합니다."
            );
        }
    }

    /**
     * 조회 계약에서 사용하는 yearMonth 검증입니다.
     *
     * YearMonth.parse()로 파싱해, "2026-13"처럼 자릿수는 맞지만
     * 실제로는 존재하지 않는 연월도 거부합니다.
     */
    private void validateYearMonthForQuery(String yearMonth) {
        if (yearMonth == null) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "yearMonth는 필수입니다."
            );
        }

        if (!isValidYearMonth(yearMonth)) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_REQUEST,
                    "yearMonth는 YYYY-MM 형식이어야 합니다."
            );
        }
    }

    /**
     * 진단 계산에 필요한 필수 입력값을 검증합니다.
     *
     * userId·yearMonth 형식처럼 호출자 요청 자체가 잘못된 경우가 아니라, 이미
     * 조회·집계된 입력 데이터의 무결성 문제이므로 {@link DiagnosisErrorCode#INVALID_CALCULATION_INPUT}
     * (500)을 던진다. 미래 연월처럼 호출자 요청 자체가 잘못된 경우는 이 메서드가 아니라
     * {@code LocalDiagnosisCommandClient}가 {@link DiagnosisErrorCode#INVALID_REQUEST}(400)로 막는다.
     */
    private void validate(DiagnosisCalculationInput input) {
        if (input == null) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "진단 계산 입력값은 필수입니다."
            );
        }

        if (input.getUserId() == null || input.getUserId() <= 0) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "userId는 양수여야 합니다."
            );
        }

        if (!isValidYearMonth(input.getYearMonth())) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "yearMonth는 YYYY-MM 형식이어야 합니다."
            );
        }

        // DIAGNOSIS_RESULT의 필수 컬럼에 저장되는 값들을 검증합니다.
        if (input.getTotalIncome() == null
                || input.getUnmatchedIncome() == null
                || input.getTotalExpense() == null
                || input.getFixedExpense() == null
                || input.getTotalFinancialAssets() == null
                || input.getLiquidAssets() == null
                || input.getSafeAssets() == null) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "진단 계산에 필요한 값이 누락되었습니다."
            );
        }

        // 소득·소비·자산은 음수일 수 없습니다. netCashFlow 같은 파생값만 음수를 허용합니다.
        if (input.getTotalIncome() < 0
                || input.getUnmatchedIncome() < 0
                || input.getTotalExpense() < 0
                || input.getFixedExpense() < 0
                || input.getTotalFinancialAssets() < 0
                || input.getLiquidAssets() < 0
                || input.getSafeAssets() < 0) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "금액 필드는 음수일 수 없습니다."
            );
        }

        // fixedExpense는 totalExpense의 부분합이어야 합니다(같은 is_consumption=true 집합 내
        // expense_type='FIXED' 필터). account-service 응답 하나의 내부 일관성을 방어적으로 검증합니다.
        if (input.getFixedExpense() > input.getTotalExpense()) {
            throw new ServiceException(
                    DiagnosisErrorCode.INVALID_CALCULATION_INPUT,
                    "고정지출은 총소비를 초과할 수 없습니다."
            );
        }
    }

    private boolean isValidYearMonth(String yearMonth) {
        if (yearMonth == null || !yearMonth.matches("[0-9]{4}-[0-9]{2}")) {
            return false;
        }
        try {
            YearMonth.parse(yearMonth);
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }
}
