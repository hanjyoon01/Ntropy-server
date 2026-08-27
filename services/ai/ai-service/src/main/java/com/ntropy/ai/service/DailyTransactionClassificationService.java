package com.ntropy.ai.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import com.ntropy.ai.client.fastapi.FastApiTransactionClassificationClient;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResult;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;
import com.ntropy.ai.port.account.ClassificationTargetTransaction;
import com.ntropy.ai.port.account.TransactionAnalysisPort;
import com.ntropy.ai.port.account.TransactionAnalysisResult;
import com.ntropy.ai.api.client.TransactionClassificationCommandClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 미분석 거래를 Spring 규칙과 FastAPI로 분류하고
 * TXN_ANALYSIS에 저장하는 일간 배치 서비스입니다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DailyTransactionClassificationService implements TransactionClassificationCommandClient {

    private static final int DB_PAGE_SIZE = 500;
    private static final int FAST_API_BATCH_SIZE = 100;

    private static final Set<String> ALLOWED_CATEGORIES =
            Set.of(
                    "FOOD",
                    "TRANSPORTATION",
                    "HOUSING",
                    "COMMUNICATION",
                    "MEDICAL",
                    "EDUCATION",
                    "SHOPPING",
                    "LEISURE",
                    "INSURANCE",
                    "FINANCE",
                    "ETC"
            );

    private static final Set<String> ALLOWED_EXPENSE_TYPES =
            Set.of(
                    "FIXED",
                    "VARIABLE"
            );

    private final TransactionAnalysisPort
            transactionAnalysisPort;

    private final FastApiTransactionClassificationClient
            fastApiClient;

    private final TransactionPreClassificationService
            preClassificationService;

    /**
     * TXN_ANALYSIS가 없는 거래가 더 이상 없을 때까지
     * 최대 500건씩 반복해서 처리합니다.
     *
     * @return 저장한 전체 거래 분석 결과 수
     */
    public int run() {
        return runPages(() -> transactionAnalysisPort
                .findUnanalyzedTransactions(DB_PAGE_SIZE));
    }

    /** 계좌 연동을 마친 특정 사용자의 미분류 거래만 즉시 처리합니다. */
    @Override
    public int classifyUnanalyzedTransactions(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("userId는 양수여야 합니다.");
        }
        return runPages(() -> transactionAnalysisPort
                .findUnanalyzedTransactionsByUserId(userId, DB_PAGE_SIZE));
    }

    private int runPages(
            Supplier<List<ClassificationTargetTransaction>> targetSupplier
    ) {
        int totalProcessed = 0;

        while (true) {
            List<ClassificationTargetTransaction> targets = targetSupplier.get();

            if (targets == null || targets.isEmpty()) {
                return totalProcessed;
            }

            List<TransactionAnalysisResult> analyses =
                    classifyPage(targets);

            transactionAnalysisPort
                    .saveDailyTransactionAnalyses(analyses);

            totalProcessed += analyses.size();

            log.info(
                    "[일간 소비 분류] 페이지 저장 완료. "
                            + "pageSize={}, totalProcessed={}",
                    analyses.size(),
                    totalProcessed
            );
        }
    }

    /**
     * 조회한 한 페이지의 거래를 Spring 결정적 규칙 대상과
     * FastAPI 대상 거래로 분리합니다.
     */
    private List<TransactionAnalysisResult> classifyPage(
            List<ClassificationTargetTransaction> targets
    ) {
        List<TransactionAnalysisResult> analyses =
                new ArrayList<>();

        List<ClassificationTargetTransaction> fastApiTargets =
                new ArrayList<>();

        for (ClassificationTargetTransaction target : targets) {
            Optional<TransactionAnalysisResult> deterministicResult =
                    preClassificationService.classify(target);

            if (deterministicResult.isPresent()) {
                analyses.add(deterministicResult.get());
            } else {
                fastApiTargets.add(target);
            }
        }

        /*
         * FastAPI #33의 요청 최대 크기가 100건이므로
         * 최대 100건씩 나눠서 호출합니다.
         */
        for (
                int start = 0;
                start < fastApiTargets.size();
                start += FAST_API_BATCH_SIZE
        ) {
            int end = Math.min(
                    start + FAST_API_BATCH_SIZE,
                    fastApiTargets.size()
            );

            analyses.addAll(
                    classifyWithFastApi(
                            fastApiTargets.subList(start, end)
                    )
            );
        }

        return analyses;
    }

    /**
     * 결정적 규칙으로 분류하지 못한 ORDINARY 거래를
     * FastAPI로 분류합니다.
     *
     * 응답 누락, 중복, 잘못된 값 또는 호출 실패는
     * ETC / VARIABLE로 확정합니다.
     */
    private List<TransactionAnalysisResult> classifyWithFastApi(
            List<ClassificationTargetTransaction> targets
    ) {
        Map<Long, ClassificationTargetTransaction> targetById =
                new HashMap<>();

        List<TransactionForClassification> request =
                new ArrayList<>();

        for (ClassificationTargetTransaction target : targets) {
            targetById.put(
                    target.transactionId(),
                    target
            );

            request.add(
                    toFastApiRequest(target)
            );
        }

        Map<Long, TransactionAnalysisResult> validResults =
                new HashMap<>();

        try {
            TransactionClassificationResponse response =
                    fastApiClient.classifyTransactions(request);

            if (response != null
                    && Boolean.TRUE.equals(response.getSuccess())
                    && response.getData() != null
                    && response.getData().getResults() != null) {

                Set<Long> duplicatedIds = new HashSet<>();

                for (
                        TransactionClassificationResult result
                        : response.getData().getResults()
                ) {
                    if (!isValidResult(
                            result,
                            targetById.keySet()
                    )) {
                        continue;
                    }

                    Long transactionId = result.getTransactionId();

                    if (validResults.containsKey(transactionId)) {
                        duplicatedIds.add(transactionId);
                        continue;
                    }

                    validResults.put(
                            transactionId,
                            toSaveItem(result)
                    );
                }

                /*
                 * 같은 transactionId가 응답에 두 번 이상 있으면
                 * 어떤 결과가 맞는지 선택하지 않고 fallback합니다.
                 */
                duplicatedIds.forEach(validResults::remove);
            }
        } catch (Exception exception) {
            log.warn(
                    "[일간 소비 분류] FastAPI 호출 실패. "
                            + "fallbackCount={}",
                    targets.size(),
                    exception
            );
        }

        List<TransactionAnalysisResult> completed =
                new ArrayList<>();

        /*
         * FastAPI 응답 순서와 관계없이 원래 요청 순서대로 저장 결과를
         * 생성하고, 결과가 없는 거래는 반드시 fallback 처리합니다.
         */
        for (ClassificationTargetTransaction target : targets) {
            completed.add(
                    validResults.getOrDefault(
                            target.transactionId(),
                            fallback(target.transactionId())
                    )
            );
        }

        return completed;
    }

    /**
     * FastAPI 응답이 요청 대상 거래이고 소비 결과 계약을
     * 만족하는지 검증합니다.
     */
    private boolean isValidResult(
            TransactionClassificationResult result,
            Set<Long> targetIds
    ) {
        if (result == null
                || result.getTransactionId() == null
                || !targetIds.contains(result.getTransactionId())
                || result.getIsConsumption() == null) {
            return false;
        }

        if (Boolean.FALSE.equals(result.getIsConsumption())) {
            return result.getCategory() == null
                    && result.getExpenseType() == null;
        }

        return ALLOWED_CATEGORIES.contains(result.getCategory())
                && ALLOWED_EXPENSE_TYPES.contains(
                result.getExpenseType()
        );
    }

    /**
     * AI #33의 구조화된 FastAPI 요청 DTO로 변환합니다.
     *
     * 이 메서드에 전달되는 거래는 ORDINARY 출금이므로
     * amount에는 outAmount를 사용합니다.
     */
    private TransactionForClassification toFastApiRequest(
            ClassificationTargetTransaction target
    ) {
        return new TransactionForClassification(
                target.transactionId(),
                target.outAmount(),
                target.transactionCategory(),
                target.organizationCode(),
                target.desc1(),
                target.desc2(),
                target.desc3(),
                target.desc4()
        );
    }

    private TransactionAnalysisResult toSaveItem(
            TransactionClassificationResult result
    ) {
        return new TransactionAnalysisResult(
                result.getTransactionId(),
                result.getIsConsumption(),
                result.getCategory(),
                result.getExpenseType()
        );
    }

    private TransactionAnalysisResult fallback(
            Long transactionId
    ) {
        return new TransactionAnalysisResult(
                transactionId,
                true,
                "ETC",
                "VARIABLE"
        );
    }
}
