package com.ntropy.account.api.client;

import java.util.List;

import com.ntropy.account.api.dto.ClassificationTargetTransaction;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import com.ntropy.account.api.dto.TransactionAnalysisSaveItem;
import com.ntropy.account.api.dto.TransactionAnalysisSaveRequest;

/**
 * AI-service가 account-service의 거래 분석 기능을 호출하기 위한
 * 내부 Client 인터페이스입니다.
 */
public interface AccountTransactionAnalysisClient {

    /**
     * 아직 TXN_ANALYSIS가 생성되지 않은 일간 소비 분석 대상 거래를
     * 조회합니다.
     *
     * @param limit 한 번에 조회할 최대 거래 수
     * @return 미분석 거래 목록
     */
    List<DailyClassificationTargetTransaction> findUnanalyzedTransactions(
            int limit
    );

    /** 특정 사용자의 아직 분석되지 않은 일간 소비 분석 대상 거래를 조회합니다. */
    List<DailyClassificationTargetTransaction> findUnanalyzedTransactionsByUserId(
            Long userId,
            int limit
    );

    /**
     * 일간 소비 분류 결과를 TXN_ANALYSIS에 저장합니다.
     *
     * 소비와 비소비 결과를 모두 저장하며,
     * account_transaction_id를 기준으로 upsert합니다.
     *
     * @param analyses 저장할 거래 분석 결과
     */
    void saveDailyTransactionAnalyses(
            List<TransactionAnalysisSaveItem> analyses
    );

    /**
     * 특정 사용자와 연월을 기준으로 AI 분류 대상 출금 거래를 조회합니다.
     *
     * @param userId 사용자 ID
     * @param yearMonth 조회 대상 연월. 예: "2026-07"
     * @return FastAPI 소비 분류 요청에 사용할 거래 목록
     */
    List<ClassificationTargetTransaction> findClassificationTargets(
            Long userId,
            String yearMonth
    );

    /**
     * FastAPI 소비 분류 결과를 account-service의
     * TXN_ANALYSIS에 저장합니다.
     *
     * @param request 저장할 분류 결과 요청
     */
    void saveTransactionAnalyses(
            TransactionAnalysisSaveRequest request
    );
}
