package com.ntropy.account.service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.api.dto.ClassificationTargetTransaction;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import com.ntropy.account.api.dto.TransactionAnalysisSaveItem;
import com.ntropy.account.api.dto.TransactionAnalysisSaveRequest;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialDataQueryMapper;
import com.ntropy.account.mapper.TxnAnalysisMapper;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TxnAnalysisService {

    private static final Pattern YEAR_MONTH_PATTERN =
            Pattern.compile("^\\d{4}-(0[1-9]|1[0-2])$");

    private static final int MAX_DAILY_QUERY_SIZE = 500;

    private final TxnAnalysisMapper txnAnalysisMapper;
    private final FinancialDataQueryMapper financialDataQueryMapper;

    /**
     * 아직 TXN_ANALYSIS가 생성되지 않은 일간 분석 대상 거래를 조회합니다.
     */
    public List<DailyClassificationTargetTransaction>
    findUnanalyzedTransactions(int limit) {
        validateDailyQueryLimit(limit);

        return financialDataQueryMapper.findUnanalyzedTransactions(limit);
    }

    /** 특정 사용자의 아직 분석되지 않은 일간 분석 대상 거래를 조회합니다. */
    public List<DailyClassificationTargetTransaction>
    findUnanalyzedTransactionsByUserId(Long userId, int limit) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "userId는 양수여야 합니다.");
        }
        validateDailyQueryLimit(limit);
        return financialDataQueryMapper.findUnanalyzedTransactionsByUserId(userId, limit);
    }

    private void validateDailyQueryLimit(int limit) {
        if (limit <= 0 || limit > MAX_DAILY_QUERY_SIZE) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "limit은 1~500이어야 합니다."
            );
        }
    }

    /**
     * 일간 소비 분류 결과를 저장합니다.
     *
     * 소비와 비소비 결과를 모두 저장하며,
     * 동일 transactionId가 존재하면 갱신합니다.
     */
    @Transactional
    public void saveDailyAnalyses(
            List<TransactionAnalysisSaveItem> analyses
    ) {
        if (analyses == null || analyses.isEmpty()) {
            return;
        }

        Set<Long> transactionIds = new LinkedHashSet<>();

        for (TransactionAnalysisSaveItem item : analyses) {
            validateDailyAnalysisItem(item, transactionIds);
        }

        txnAnalysisMapper.upsertDailyAnalyses(analyses);
    }

    private void validateDailyAnalysisItem(
            TransactionAnalysisSaveItem item,
            Set<Long> transactionIds
    ) {
        if (item == null
                || item.getTransactionId() == null
                || item.getTransactionId() <= 0) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "유효한 거래 분석 항목이 필요합니다."
            );
        }

        if (!transactionIds.add(item.getTransactionId())) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "중복된 transactionId가 포함되어 있습니다."
            );
        }

        if (item.getIsConsumption() == null) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "isConsumption이 필요합니다."
            );
        }

        if (Boolean.FALSE.equals(item.getIsConsumption())
                && (
                item.getCategory() != null
                        || item.getExpenseType() != null
        )) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "비소비 거래의 분류 값은 null이어야 합니다."
            );
        }

        if (Boolean.TRUE.equals(item.getIsConsumption())
                && (
                item.getCategory() == null
                        || item.getExpenseType() == null
        )) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "소비 거래의 분류 값이 필요합니다."
            );
        }
    }

    /**
     * 기존 월간 소비 분류 흐름에서 사용하는 조회 메서드입니다.
     */
    public List<ClassificationTargetTransaction> findClassificationTargets(
            Long userId,
            String yearMonth
    ) {
        return financialDataQueryMapper.findClassificationTargets(
                userId,
                yearMonth
        );
    }

    /**
     * 기존 월간 소비 분류 흐름에서 사용하는 저장 메서드입니다.
     */
    @Transactional
    public void saveAnalyses(
            TransactionAnalysisSaveRequest request
    ) {
        if (request == null
                || request.getAnalyses() == null
                || request.getAnalyses().isEmpty()) {
            return;
        }

        Long userId = request.getUserId();
        String yearMonth = request.getYearMonth();

        if (userId == null || userId <= 0) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "userId가 유효하지 않습니다."
            );
        }

        if (yearMonth == null
                || !YEAR_MONTH_PATTERN.matcher(yearMonth).matches()) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "yearMonth 형식이 올바르지 않습니다."
            );
        }

        Set<Long> requestedTransactionIds = new LinkedHashSet<>();

        for (TransactionAnalysisSaveItem item : request.getAnalyses()) {
            if (item == null) {
                throw new ServiceException(
                        AccountErrorCode.INVALID_REQUEST,
                        "거래 분석 항목이 필요합니다."
                );
            }

            Long transactionId = item.getTransactionId();

            if (transactionId == null || transactionId <= 0) {
                throw new ServiceException(
                        AccountErrorCode.INVALID_REQUEST,
                        "transactionId는 양수여야 합니다."
                );
            }

            if (!requestedTransactionIds.add(transactionId)) {
                throw new ServiceException(
                        AccountErrorCode.INVALID_REQUEST,
                        "중복된 transactionId가 포함되어 있습니다."
                );
            }
        }

        List<Long> validTransactionIds =
                financialDataQueryMapper.findValidTransactionIds(
                        userId,
                        yearMonth,
                        List.copyOf(requestedTransactionIds)
                );

        if (!new LinkedHashSet<>(validTransactionIds)
                .equals(requestedTransactionIds)) {
            throw new ServiceException(
                    AccountErrorCode.TRANSACTION_ANALYSIS_TARGET_INVALID
            );
        }

        txnAnalysisMapper.upsertAnalyses(request);
    }
}
