package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.FinancialCommitmentSummary;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialCommitmentMapper;
import com.ntropy.account.mapper.projection.InsuranceOutflowRow;
import com.ntropy.account.mapper.projection.LoanCommitmentCandidateRow;
import com.ntropy.account.mapper.projection.SavingCommitmentCandidateRow;
import com.ntropy.common.exception.ServiceException;

class FinancialCommitmentServiceTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-15T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void returnsEmptyListWhenNothingToReport() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(List.of(), result);
    }

    @Test
    void savingUsesLatestInstallmentAmountAndAccountNextPaymentDate() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금계좌", LocalDate.of(2026, 8, 25), "100000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.size());
        FinancialCommitmentSummary summary = result.get(0);
        assertEquals("SAVING_PAYMENT", summary.getExpenseType());
        assertEquals(10L, summary.getAccountId());
        assertEquals(100000L, summary.getExpectedAmount());
        assertEquals(LocalDate.of(2026, 8, 25), summary.getNextPaymentDate());
        assertEquals("ESTIMATED", summary.getAmountStatus());
        assertEquals("ESTIMATED", summary.getDateStatus());
        assertEquals(null, summary.getOutstandingBalance());
        assertNull(summary.getExpectedPrincipalAmount());
        assertNull(summary.getExpectedInterestAmount());
    }

    @Test
    void savingWithoutInstallmentHistoryIsInsufficient() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금계좌", LocalDate.of(2026, 8, 25), null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(null, summary.getExpectedAmount());
        assertEquals("INSUFFICIENT", summary.getAmountStatus());
    }

    @Test
    void savingWithZeroInstallmentAmountIsInsufficient() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금계좌", LocalDate.of(2026, 8, 25), "0.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(null, summary.getExpectedAmount());
        assertEquals("INSUFFICIENT", summary.getAmountStatus());
    }

    @Test
    void savingWithoutNextPaymentDateIsIncludedRegardlessOfRangeWithInsufficientDateStatus() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금계좌", null, "50000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 31));

        assertEquals(1, result.size());
        assertEquals(null, result.get(0).getNextPaymentDate());
        assertEquals("INSUFFICIENT", result.get(0).getDateStatus());
    }

    @Test
    void savingOutsideDateRangeIsExcluded() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금계좌", LocalDate.of(2026, 9, 25), "50000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(List.of(), result);
    }

    @Test
    void loanUsesOutstandingBalanceAndLatestNormalRepaymentAmount() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25), "250000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals("LOAN_REPAYMENT", summary.getExpenseType());
        assertEquals(3000000L, summary.getOutstandingBalance());
        assertEquals(250000L, summary.getExpectedAmount());
        assertEquals("ESTIMATED", summary.getAmountStatus());
    }

    @Test
    void loanWithoutNormalRepaymentCandidateIsInsufficient() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25), null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(null, summary.getExpectedAmount());
        assertEquals("INSUFFICIENT", summary.getAmountStatus());
        assertEquals(3000000L, summary.getOutstandingBalance());
        assertNull(summary.getExpectedPrincipalAmount());
        assertNull(summary.getExpectedInterestAmount());
    }

    @Test
    void loanSplitsTotalIntoPrincipalAndInterestWhenBothAvailable() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25),
                "250000.00", "200000.00", "50000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(250000L, summary.getExpectedAmount());
        assertEquals(200000L, summary.getExpectedPrincipalAmount());
        assertEquals(50000L, summary.getExpectedInterestAmount());
    }

    @Test
    void loanTotalFallsBackToPrincipalPlusInterestWhenTotalColumnIsUnresolvable() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25),
                null, "200000.00", "50000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(250000L, summary.getExpectedAmount());
        assertEquals("ESTIMATED", summary.getAmountStatus());
        assertEquals(200000L, summary.getExpectedPrincipalAmount());
        assertEquals(50000L, summary.getExpectedInterestAmount());
    }

    @Test
    void loanTotalStaysInsufficientWhenColumnMissingAndOnlyOneOfPrincipalOrInterestAvailable() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25),
                null, "200000.00", null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(null, summary.getExpectedAmount());
        assertEquals("INSUFFICIENT", summary.getAmountStatus());
        assertEquals(200000L, summary.getExpectedPrincipalAmount());
        assertNull(summary.getExpectedInterestAmount());
    }

    @Test
    void loanMissingPrincipalDoesNotBorrowFromTotalRepaymentAmount() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25),
                "250000.00", null, "50000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(250000L, summary.getExpectedAmount());
        assertEquals("ESTIMATED", summary.getAmountStatus());
        assertNull(summary.getExpectedPrincipalAmount());
        assertEquals(50000L, summary.getExpectedInterestAmount());
    }

    @Test
    void loanZeroPrincipalOrInterestIsPreservedNotTreatedAsInsufficient() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.loans.add(loan(20L, "대출계좌", "3000000.00", LocalDate.of(2026, 8, 25),
                "250000.00", "0.00", "250000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        FinancialCommitmentSummary summary = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)).get(0);

        assertEquals(0L, summary.getExpectedPrincipalAmount());
        assertEquals(250000L, summary.getExpectedInterestAmount());
    }

    @Test
    void fractionalOrOverflowAmountIsIsolatedToThatItemAsInsufficient() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금A", LocalDate.of(2026, 8, 25), "100000.50"));
        mapper.savings.add(saving(11L, "적금B", LocalDate.of(2026, 8, 26), "50000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(2, result.size());
        FinancialCommitmentSummary broken = result.stream()
                .filter(s -> s.getAccountId().equals(10L)).findFirst().orElseThrow();
        FinancialCommitmentSummary ok = result.stream()
                .filter(s -> s.getAccountId().equals(11L)).findFirst().orElseThrow();
        assertEquals(null, broken.getExpectedAmount());
        assertEquals("INSUFFICIENT", broken.getAmountStatus());
        assertEquals(50000L, ok.getExpectedAmount());
        assertEquals("ESTIMATED", ok.getAmountStatus());
    }

    @Test
    void insuranceSingleOccurrenceWithKnownInsurerNameIsReportedImmediately() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "50000.00", "삼성생명보험", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.size());
        FinancialCommitmentSummary summary = result.get(0);
        assertEquals("INSURANCE_PREMIUM", summary.getExpenseType());
        assertEquals("삼성생명보험", summary.getProductName());
        assertEquals(50000L, summary.getExpectedAmount());
        assertEquals(LocalDate.of(2026, 8, 5), summary.getNextPaymentDate());
        assertNull(summary.getAccountId());
        assertEquals("ESTIMATED", summary.getAmountStatus());
        assertEquals("ESTIMATED", summary.getDateStatus());
    }

    @Test
    void insuranceSeparatesProductsFromSameInsurerWithoutSummingThem() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 3), "62000.00", null, "보험료", "삼성생명 실손보험", null));
        mapper.insurance.add(insuranceRow(31L, LocalDate.of(2026, 7, 20), "43000.00", null, "보험료", "삼성생명 운전자보험", null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(2, result.size());
        assertEquals(Set.of("삼성생명 실손보험", "삼성생명 운전자보험"),
                result.stream().map(FinancialCommitmentSummary::getProductName).collect(Collectors.toSet()));
        assertEquals(Set.of(62000L, 43000L),
                result.stream().map(FinancialCommitmentSummary::getExpectedAmount).collect(Collectors.toSet()));
        assertTrue(result.stream().allMatch(summary -> summary.getAccountId() == null));
    }

    @Test
    void insuranceUsesLatestOccurrenceAmountForSameProductWithoutSumming() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 6, 5), "999999.00", "삼성생명", null, null, null));
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 20), "50000.00", "삼성생명", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(1, result.size());
        assertEquals(50000L, result.get(0).getExpectedAmount());
        assertEquals(LocalDate.of(2026, 8, 20), result.get(0).getNextPaymentDate());
    }

    @Test
    void insuranceKeepsDifferentInsurersSeparate() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "50000.00", "삼성생명보험", null, null, null));
        mapper.insurance.add(insuranceRow(31L, LocalDate.of(2026, 7, 10), "40000.00", "DB손해보험㈜", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(2, result.size());
        FinancialCommitmentSummary samsung = result.stream()
                .filter(s -> "삼성생명보험".equals(s.getProductName())).findFirst().orElseThrow();
        FinancialCommitmentSummary db = result.stream()
                .filter(s -> "DB손해보험㈜".equals(s.getProductName())).findFirst().orElseThrow();
        assertEquals(50000L, samsung.getExpectedAmount());
        assertEquals(40000L, db.getExpectedAmount());
    }

    @Test
    void insurancePublicInsuranceIsIncludedAsInsurancePremium() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "112000.00", "국민건강보험공단", null, null, null));
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "47000.00", "국민연금", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> "INSURANCE_PREMIUM".equals(s.getExpenseType())));
        assertEquals(Set.of("국민건강보험공단", "국민연금"),
                result.stream().map(FinancialCommitmentSummary::getProductName).collect(Collectors.toSet()));
    }

    @Test
    void insuranceExcludesNonInsurerRecurringPaymentsEvenWhenRepeatedWithSameAmount() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "50000.00", "체크카드승인", null, null, null));
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 20), "50000.00", "FBS 출금", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(List.of(), result);
    }

    @Test
    void insuranceRowsWithAllDescFieldsBlankAreExcludedFromGrouping() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 6, 5), "50000.00", "", " ", null, null));
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "50000.00", "", " ", null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(List.of(), result);
    }

    @Test
    void insuranceEstimatedNextPaymentDateOutsideRangeIsExcluded() {
        // observation window for FIXED_CLOCK(2026-08-15)/toDate(2026-08-31) is [2026-05-15, 2026-08-15],
        // so both occurrences must stay inside that window while still landing outside [fromDate, toDate].
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 5, 20), "50000.00", "삼성생명", null, null, null));
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 6, 5), "50000.00", "삼성생명", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(List.of(), result);
    }

    @Test
    void insuranceObservationWindowIsClampedToTodayWhenToDateIsInTheFuture() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        service.findFinancialCommitments(1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31));

        assertEquals(LocalDate.of(2026, 8, 15), mapper.lastObservationEnd);
        assertEquals(LocalDate.of(2026, 5, 15), mapper.lastObservationStart);
    }

    @Test
    void insuranceObservationWindowUsesToDateWhenItIsBeforeToday() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        service.findFinancialCommitments(1L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

        assertEquals(LocalDate.of(2026, 1, 31), mapper.lastObservationEnd);
        assertEquals(LocalDate.of(2025, 10, 31), mapper.lastObservationStart);
    }

    @Test
    void sortsByNextPaymentDateThenExpenseTypeThenAccountIdWithNullsLast() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.savings.add(saving(10L, "적금", LocalDate.of(2026, 8, 20), "10000.00"));
        mapper.loans.add(loan(20L, "대출", "100000.00", LocalDate.of(2026, 8, 10), "10000.00"));
        mapper.savings.add(saving(11L, "적금무기한", null, "10000.00"));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(List.of(20L, 10L, 11L), result.stream().map(FinancialCommitmentSummary::getAccountId).toList());
    }

    @Test
    void insuranceItemsWithNullAccountIdAndSameNextPaymentDateAreSortedByProductName() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        mapper.insurance.add(insuranceRow(30L, LocalDate.of(2026, 7, 5), "50000.00", "삼성생명보험", null, null, null));
        mapper.insurance.add(insuranceRow(31L, LocalDate.of(2026, 7, 5), "40000.00", "DB손해보험", null, null, null));
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        List<FinancialCommitmentSummary> result = service.findFinancialCommitments(
                1L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(s -> s.getAccountId() == null));
        assertEquals(List.of("DB손해보험", "삼성생명보험"),
                result.stream().map(FinancialCommitmentSummary::getProductName).toList());
    }

    @Test
    void rejectsNullOrNonPositiveUserId() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        assertThrowsInvalidRequest(() -> service.findFinancialCommitments(
                null, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
        assertThrowsInvalidRequest(() -> service.findFinancialCommitments(
                0L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));
    }

    @Test
    void rejectsNullDates() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        assertThrowsInvalidRequest(() -> service.findFinancialCommitments(1L, null, LocalDate.of(2026, 8, 31)));
        assertThrowsInvalidRequest(() -> service.findFinancialCommitments(1L, LocalDate.of(2026, 8, 1), null));
    }

    @Test
    void rejectsFromDateAfterToDate() {
        InMemoryFinancialCommitmentMapper mapper = new InMemoryFinancialCommitmentMapper();
        FinancialCommitmentService service = new FinancialCommitmentService(mapper, FIXED_CLOCK);

        assertThrowsInvalidRequest(() -> service.findFinancialCommitments(
                1L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1)));
    }

    private static void assertThrowsInvalidRequest(Runnable action) {
        ServiceException exception = assertThrows(ServiceException.class, action::run);
        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    private static SavingCommitmentCandidateRow saving(
            Long accountId, String productName, LocalDate nextPaymentDate, String expectedAmount) {
        SavingCommitmentCandidateRow row = new SavingCommitmentCandidateRow();
        row.setAccountId(accountId);
        row.setProductName(productName);
        row.setNextPaymentDate(nextPaymentDate);
        row.setExpectedAmount(expectedAmount == null ? null : new BigDecimal(expectedAmount));
        return row;
    }

    private static LoanCommitmentCandidateRow loan(
            Long accountId, String productName, String outstandingBalance,
            LocalDate nextPaymentDate, String expectedAmount) {
        return loan(accountId, productName, outstandingBalance, nextPaymentDate, expectedAmount, null, null);
    }

    private static LoanCommitmentCandidateRow loan(
            Long accountId, String productName, String outstandingBalance, LocalDate nextPaymentDate,
            String expectedAmount, String expectedPrincipalAmount, String expectedInterestAmount) {
        LoanCommitmentCandidateRow row = new LoanCommitmentCandidateRow();
        row.setAccountId(accountId);
        row.setProductName(productName);
        row.setOutstandingBalance(outstandingBalance == null ? null : new BigDecimal(outstandingBalance));
        row.setNextPaymentDate(nextPaymentDate);
        row.setExpectedAmount(expectedAmount == null ? null : new BigDecimal(expectedAmount));
        row.setExpectedPrincipalAmount(expectedPrincipalAmount == null ? null : new BigDecimal(expectedPrincipalAmount));
        row.setExpectedInterestAmount(expectedInterestAmount == null ? null : new BigDecimal(expectedInterestAmount));
        return row;
    }

    private static InsuranceOutflowRow insuranceRow(
            Long accountId, LocalDate tranDate, String outAmount,
            String desc1, String desc2, String desc3, String desc4) {
        InsuranceOutflowRow row = new InsuranceOutflowRow();
        row.setAccountId(accountId);
        row.setTranDate(tranDate);
        row.setOutAmount(outAmount == null ? null : new BigDecimal(outAmount));
        row.setDesc1(desc1);
        row.setDesc2(desc2);
        row.setDesc3(desc3);
        row.setDesc4(desc4);
        return row;
    }

    private static class InMemoryFinancialCommitmentMapper implements FinancialCommitmentMapper {

        private final List<SavingCommitmentCandidateRow> savings = new ArrayList<>();
        private final List<LoanCommitmentCandidateRow> loans = new ArrayList<>();
        private final List<InsuranceOutflowRow> insurance = new ArrayList<>();
        private LocalDate lastObservationStart;
        private LocalDate lastObservationEnd;

        @Override
        public List<SavingCommitmentCandidateRow> findSavingCommitmentCandidates(Long userId) {
            return savings;
        }

        @Override
        public List<LoanCommitmentCandidateRow> findLoanCommitmentCandidates(
                Long userId, List<String> loanDisbursementKeywords) {
            return loans;
        }

        @Override
        public List<InsuranceOutflowRow> findInsuranceOutflowCandidates(
                Long userId, LocalDate observationStartDate, LocalDate observationEndDate) {
            lastObservationStart = observationStartDate;
            lastObservationEnd = observationEndDate;
            return insurance.stream()
                    .filter(row -> !row.getTranDate().isBefore(observationStartDate)
                            && !row.getTranDate().isAfter(observationEndDate))
                    .toList();
        }
    }
}
