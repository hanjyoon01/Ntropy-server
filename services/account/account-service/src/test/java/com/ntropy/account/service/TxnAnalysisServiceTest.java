package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.ClassificationTargetTransaction;
import com.ntropy.account.api.dto.DailyClassificationTargetTransaction;
import com.ntropy.account.api.dto.TransactionAnalysisSaveItem;
import com.ntropy.account.api.dto.TransactionAnalysisSaveRequest;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.TxnAnalysis;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialDataQueryMapper;
import com.ntropy.account.mapper.TxnAnalysisMapper;
import com.ntropy.account.mapper.projection.OwnedAccountTransactionRow;
import com.ntropy.account.mapper.projection.OwnedTransactionCountRow;
import com.ntropy.common.exception.ServiceException;

class TxnAnalysisServiceTest {

    @Test
    void savesAnalysesForOwnedTransactionsInRequestedMonth() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        financialDataQueryMapper.addAccount(10L, 1L, "ACTIVE");
        financialDataQueryMapper.addTransaction(101L, 10L, "2026-08");
        financialDataQueryMapper.addTransaction(102L, 10L, "2026-08");
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        service.saveAnalyses(request(1L, "2026-08", 101L, 102L));

        assertEquals(1, txnAnalysisMapper.upsertAnalysesCalls);
        assertEquals(2, txnAnalysisMapper.savedRows);
    }

    @Test
    void rejectsRequestContainingAnotherUsersTransaction() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        financialDataQueryMapper.addAccount(10L, 1L, "ACTIVE");
        financialDataQueryMapper.addAccount(20L, 2L, "ACTIVE");
        financialDataQueryMapper.addTransaction(101L, 10L, "2026-08");
        financialDataQueryMapper.addTransaction(201L, 20L, "2026-08");
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", 101L, 201L)));

        assertEquals(AccountErrorCode.TRANSACTION_ANALYSIS_TARGET_INVALID.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
        assertEquals(0, txnAnalysisMapper.savedRows);
    }

    @Test
    void rejectsRequestContainingTransactionFromAnotherMonth() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        financialDataQueryMapper.addAccount(10L, 1L, "ACTIVE");
        financialDataQueryMapper.addTransaction(101L, 10L, "2026-08");
        financialDataQueryMapper.addTransaction(102L, 10L, "2026-07");
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", 101L, 102L)));

        assertEquals(AccountErrorCode.TRANSACTION_ANALYSIS_TARGET_INVALID.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.savedRows);
    }

    @Test
    void rejectsRequestContainingNonexistentTransaction() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        financialDataQueryMapper.addAccount(10L, 1L, "ACTIVE");
        financialDataQueryMapper.addTransaction(101L, 10L, "2026-08");
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", 101L, 999L)));

        assertEquals(AccountErrorCode.TRANSACTION_ANALYSIS_TARGET_INVALID.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.savedRows);
    }

    @Test
    void rejectsMixedValidAndInvalidBatchWithoutPartialSave() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        financialDataQueryMapper.addAccount(10L, 1L, "ACTIVE");
        financialDataQueryMapper.addAccount(20L, 2L, "ACTIVE");
        financialDataQueryMapper.addTransaction(101L, 10L, "2026-08");
        financialDataQueryMapper.addTransaction(102L, 10L, "2026-08");
        financialDataQueryMapper.addTransaction(201L, 20L, "2026-08");
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", 101L, 102L, 201L, 999L)));

        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
        assertEquals(0, txnAnalysisMapper.savedRows);
    }

    @Test
    void returnsWithoutSavingForEmptyAnalysesList() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        service.saveAnalyses(new TransactionAnalysisSaveRequest(1L, "2026-08", List.of()));
        service.saveAnalyses(null);

        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
    }

    @Test
    void rejectsInvalidUserId() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(null, "2026-08", 101L)));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
    }

    @Test
    void rejectsInvalidYearMonthFormat() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-8", 101L)));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
    }

    @Test
    void rejectsNullAnalysisItem() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);
        List<TransactionAnalysisSaveItem> analyses = new ArrayList<>();
        analyses.add(null);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(new TransactionAnalysisSaveRequest(1L, "2026-08", analyses)));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
    }

    @Test
    void rejectsNullOrNonPositiveTransactionId() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException nullIdException = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", (Long) null)));
        ServiceException zeroIdException = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", 0L)));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), nullIdException.getStatusCode());
        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), zeroIdException.getStatusCode());
        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
    }

    @Test
    void rejectsDuplicateTransactionIds() {
        InMemoryFinancialDataQueryMapper financialDataQueryMapper = new InMemoryFinancialDataQueryMapper();
        InMemoryTxnAnalysisMapper txnAnalysisMapper = new InMemoryTxnAnalysisMapper();
        TxnAnalysisService service = new TxnAnalysisService(txnAnalysisMapper, financialDataQueryMapper);

        ServiceException exception = assertThrows(ServiceException.class,
                () -> service.saveAnalyses(request(1L, "2026-08", 101L, 101L)));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
        assertEquals(0, txnAnalysisMapper.upsertAnalysesCalls);
    }

    @Test
    void savesCompletedConsumptionAndNonConsumptionDailyResults() {
        InMemoryFinancialDataQueryMapper queryMapper =
                new InMemoryFinancialDataQueryMapper();

        InMemoryTxnAnalysisMapper analysisMapper =
                new InMemoryTxnAnalysisMapper();

        TxnAnalysisService service =
                new TxnAnalysisService(
                        analysisMapper,
                        queryMapper
                );

        service.saveDailyAnalyses(
                List.of(
                        new TransactionAnalysisSaveItem(
                                1L,
                                true,
                                "FOOD",
                                "VARIABLE"
                        ),
                        new TransactionAnalysisSaveItem(
                                2L,
                                false,
                                null,
                                null
                        )
                )
        );

        assertEquals(
                2,
                analysisMapper.savedRows
        );
    }

    @Test
    void rejectsInconsistentDailyResult() {
        InMemoryFinancialDataQueryMapper queryMapper =
                new InMemoryFinancialDataQueryMapper();

        InMemoryTxnAnalysisMapper analysisMapper =
                new InMemoryTxnAnalysisMapper();

        TxnAnalysisService service =
                new TxnAnalysisService(
                        analysisMapper,
                        queryMapper
                );

        assertThrows(
                ServiceException.class,
                () -> service.saveDailyAnalyses(
                        List.of(
                                new TransactionAnalysisSaveItem(
                                        1L,
                                        false,
                                        "FOOD",
                                        "VARIABLE"
                                )
                        )
                )
        );

        assertThrows(
                ServiceException.class,
                () -> service.saveDailyAnalyses(
                        List.of(
                                new TransactionAnalysisSaveItem(
                                        2L,
                                        true,
                                        null,
                                        null
                                )
                        )
                )
        );

        assertEquals(
                0,
                analysisMapper.savedRows
        );
    }

    @Test
    void rejectsDailyQueryLimitOutsideOneToFiveHundred() {
        TxnAnalysisService service =
                new TxnAnalysisService(
                        new InMemoryTxnAnalysisMapper(),
                        new InMemoryFinancialDataQueryMapper()
                );

        assertThrows(
                ServiceException.class,
                () -> service.findUnanalyzedTransactions(0)
        );

        assertThrows(
                ServiceException.class,
                () -> service.findUnanalyzedTransactions(501)
        );
    }


    private static TransactionAnalysisSaveRequest request(Long userId, String yearMonth, Long... transactionIds) {
        List<TransactionAnalysisSaveItem> analyses = new ArrayList<>();
        for (Long transactionId : transactionIds) {
            analyses.add(new TransactionAnalysisSaveItem(transactionId, true, "FOOD", "VARIABLE"));
        }
        return new TransactionAnalysisSaveRequest(userId, yearMonth, analyses);
    }

    private static class InMemoryTxnAnalysisMapper implements TxnAnalysisMapper {

        private int upsertAnalysesCalls = 0;
        private int savedRows = 0;

        @Override
        public int upsert(TxnAnalysis txnAnalysis) {
            savedRows++;
            return 1;
        }

        @Override
        public int upsertAnalyses(TransactionAnalysisSaveRequest request) {
            upsertAnalysesCalls++;
            savedRows += request.getAnalyses().size();
            return request.getAnalyses().size();
        }

        @Override
        public int upsertDailyAnalyses(
                List<TransactionAnalysisSaveItem> analyses
        ) {
            savedRows += analyses.size();
            return analyses.size();
        }
    }

    private record TxnRow(Long transactionId, Long accountId, String yearMonth) {
    }

    private record AccRow(Long accountId, Long userId, String status) {
    }

    private static class InMemoryFinancialDataQueryMapper implements FinancialDataQueryMapper {

        private final List<AccRow> accounts = new ArrayList<>();
        private final List<TxnRow> transactions = new ArrayList<>();

        void addAccount(Long accountId, Long userId, String status) {
            accounts.add(new AccRow(accountId, userId, status));
        }

        void addTransaction(Long transactionId, Long accountId, String yearMonth) {
            transactions.add(new TxnRow(transactionId, accountId, yearMonth));
        }

        @Override
        public List<Account> findAccountsByUserId(Long userId) {
            return List.of();
        }

        @Override
        public Account findAccountByIdAndUserId(Long accountId, Long userId) {
            return null;
        }

        @Override
        public OwnedTransactionCountRow countTransactionsByAccountIdAndUserId(
                Long accountId, Long userId, LocalDate startDate, LocalDate endDate
        ) {
            return null;
        }

        @Override
        public List<OwnedAccountTransactionRow> findTransactionsByAccountIdAndUserId(
                Long accountId, Long userId, LocalDate startDate, LocalDate endDate, int limit, int offset
        ) {
            return List.of();
        }

        @Override
        public List<ClassificationTargetTransaction> findClassificationTargets(Long userId, String yearMonth) {
            return List.of();
        }

        @Override
        public List<Long> findValidTransactionIds(Long userId, String yearMonth, List<Long> transactionIds) {
            Set<Long> ownedActiveAccountIds = accounts.stream()
                    .filter(a -> userId.equals(a.userId()) && "ACTIVE".equals(a.status()))
                    .map(AccRow::accountId)
                    .collect(Collectors.toSet());

            return transactions.stream()
                    .filter(t -> transactionIds.contains(t.transactionId()))
                    .filter(t -> ownedActiveAccountIds.contains(t.accountId()))
                    .filter(t -> yearMonth.equals(t.yearMonth()))
                    .map(TxnRow::transactionId)
                    .toList();
        }

        @Override
        public List<DailyClassificationTargetTransaction>
                findUnanalyzedTransactions(int limit) {
            return List.of();
        }

        @Override
        public List<DailyClassificationTargetTransaction>
                findUnanalyzedTransactionsByUserId(Long userId, int limit) {
            return List.of();
        }
    }
}
