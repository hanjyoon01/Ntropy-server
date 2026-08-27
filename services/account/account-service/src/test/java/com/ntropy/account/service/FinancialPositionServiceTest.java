package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.api.dto.FinancialPositionSummary;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialPositionMapper;
import com.ntropy.account.mapper.projection.FinancialPositionAccountRow;
import com.ntropy.common.exception.ServiceException;

class FinancialPositionServiceTest {

    @Test
    void returnsAllZeroWhenUserHasNoAggregationTargetAccounts() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(new FinancialPositionSummary(0L, 0L, 0L, 0L, 0L), summary);
    }

    @Test
    void aggregatesLiquidSafeAndLiabilityBalancesSeparately() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, "100000.00"));
        mapper.add(row(2L, "11", AccountGroup.DEPOSIT_TRUST, null, "50000.00"));
        mapper.add(row(3L, "12", AccountGroup.DEPOSIT_TRUST, null, "300000.00"));
        mapper.add(row(4L, "40", AccountGroup.LOAN, null, "200000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(150000L, summary.liquidAssets());
        assertEquals(300000L, summary.safeAssets());
        assertEquals(450000L, summary.totalFinancialAssets());
        assertEquals(200000L, summary.totalLiabilities());
        assertEquals(250000L, summary.netFinancialAssets());
    }

    @Test
    void allowsNegativeNetFinancialAssetsWhenLiabilitiesExceedAssets() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, "100000.00"));
        mapper.add(row(2L, "40", AccountGroup.LOAN, null, "900000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(-800000L, summary.netFinancialAssets());
    }

    @Test
    void excludesOverdraftOrdinaryAccountFromLiquidAssetsWithoutValidatingItsBalance() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, "100000.00"));
        mapper.add(row(2L, "11", AccountGroup.DEPOSIT_TRUST, true, null));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(100000L, summary.liquidAssets());
    }

    @Test
    void aggregatesLiabilityByDepositTypeCodeAndLoanGroup() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "40", AccountGroup.LOAN, null, "200000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(200000L, summary.totalLiabilities());
        assertEquals(-200000L, summary.netFinancialAssets());
    }

    @Test
    void excludesDepositTypeCode40RowFromLiabilitiesWhenAccountGroupIsNotLoan() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "40", AccountGroup.DEPOSIT_TRUST, null, null));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(new FinancialPositionSummary(0L, 0L, 0L, 0L, 0L), summary);
    }

    @Test
    void rejectsNullBalanceOnAggregationTargetAccount() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, null));
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(1L));

        assertEquals(AccountErrorCode.FINANCIAL_POSITION_BALANCE_INVALID.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void rejectsNegativeBalanceOnAggregationTargetAccount() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "12", AccountGroup.DEPOSIT_TRUST, null, "-1000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(1L));

        assertEquals(AccountErrorCode.FINANCIAL_POSITION_BALANCE_INVALID.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void current_rejectsNegativeLoanBalanceInsteadOfNormalizingIt() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "40", AccountGroup.LOAN, null, "-500000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.findFinancialPosition(1L)
        );

        assertEquals(AccountErrorCode.FINANCIAL_POSITION_BALANCE_INVALID.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void rejectsFractionalWonBalance() {
        InMemoryFinancialPositionMapper mapperOneCent = new InMemoryFinancialPositionMapper();
        mapperOneCent.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, "100000.01"));
        InMemoryFinancialPositionMapper mapperHalf = new InMemoryFinancialPositionMapper();
        mapperHalf.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, "100000.50"));

        assertThrows(ServiceException.class,
                () -> new FinancialPositionService(mapperOneCent).findFinancialPosition(1L));
        assertThrows(ServiceException.class,
                () -> new FinancialPositionService(mapperHalf).findFinancialPosition(1L));
    }

    @Test
    void acceptsBalanceWithZeroFraction() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "12", AccountGroup.DEPOSIT_TRUST, null, "100000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L);

        assertEquals(100000L, summary.safeAssets());
    }

    @Test
    void doesNotCancelOutFractionalBalancesAcrossAccounts() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "12", AccountGroup.DEPOSIT_TRUST, null, "100000.50"));
        mapper.add(row(2L, "12", AccountGroup.DEPOSIT_TRUST, null, "99999.50"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        assertThrows(ServiceException.class, () -> service.findFinancialPosition(1L));
    }

    @Test
    void throwsOverflowWhenSumExceedsLongRange() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, new BigDecimal(Long.MAX_VALUE).toPlainString() + ".00"));
        mapper.add(row(2L, "11", AccountGroup.DEPOSIT_TRUST, null, "1.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(1L));

        assertEquals(AccountErrorCode.FINANCIAL_POSITION_OVERFLOW.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void rejectsNullOrNonPositiveUserId() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException nullUserId = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(null));
        ServiceException zeroUserId = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(0L));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), nullUserId.getStatusCode());
        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), zeroUserId.getStatusCode());
    }

    @Test
    void asOf_aggregatesFromAsOfMapperMethodSeparatelyFromCurrentMethod() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.add(row(1L, "11", AccountGroup.DEPOSIT_TRUST, null, "999999.00"));
        mapper.addAsOf(row(2L, "12", AccountGroup.DEPOSIT_TRUST, null, "300000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L, LocalDate.of(2026, 6, 30));

        // 현재 잔액 조회용 row(999999)가 아니라 asOf 전용 row(300000)만 반영돼야 한다.
        assertEquals(300000L, summary.safeAssets());
        assertEquals(0L, summary.liquidAssets());
    }

    @Test
    void asOf_normalizesNegativeLoanBalanceSignInsteadOfRejecting() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.addAsOf(row(1L, "40", AccountGroup.LOAN, null, "-500000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        FinancialPositionSummary summary = service.findFinancialPosition(1L, LocalDate.of(2026, 6, 30));

        assertEquals(500000L, summary.totalLiabilities());
    }

    @Test
    void asOf_stillRejectsNegativeBalanceForNonLoanBucket() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        mapper.addAsOf(row(1L, "12", AccountGroup.DEPOSIT_TRUST, null, "-1000.00"));
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.findFinancialPosition(1L, LocalDate.of(2026, 6, 30)));

        assertEquals(AccountErrorCode.FINANCIAL_POSITION_BALANCE_INVALID.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void asOf_rejectsNullAsOf() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        FinancialPositionService service = new FinancialPositionService(mapper);

        ServiceException exception = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(1L, null));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void asOf_rejectsNullOrNonPositiveUserId() {
        InMemoryFinancialPositionMapper mapper = new InMemoryFinancialPositionMapper();
        FinancialPositionService service = new FinancialPositionService(mapper);
        LocalDate asOf = LocalDate.of(2026, 6, 30);

        ServiceException nullUserId = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(null, asOf));
        ServiceException zeroUserId = assertThrows(
                ServiceException.class, () -> service.findFinancialPosition(0L, asOf));

        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), nullUserId.getStatusCode());
        assertEquals(AccountErrorCode.INVALID_REQUEST.getStatusCode(), zeroUserId.getStatusCode());
    }

    private static FinancialPositionAccountRow row(
            Long accountId, String depositTypeCode, AccountGroup accountGroup, Boolean overdraftYn, String balance
    ) {
        FinancialPositionAccountRow row = new FinancialPositionAccountRow();
        row.setAccountId(accountId);
        row.setDepositTypeCode(depositTypeCode);
        row.setAccountGroup(accountGroup);
        row.setOverdraftYn(overdraftYn);
        row.setBalance(balance == null ? null : new BigDecimal(balance));
        return row;
    }

    private static class InMemoryFinancialPositionMapper implements FinancialPositionMapper {

        private final List<FinancialPositionAccountRow> rows = new ArrayList<>();
        private final List<FinancialPositionAccountRow> asOfRows = new ArrayList<>();

        void add(FinancialPositionAccountRow row) {
            rows.add(row);
        }

        void addAsOf(FinancialPositionAccountRow row) {
            asOfRows.add(row);
        }

        @Override
        public List<FinancialPositionAccountRow> findFinancialPositionAccounts(Long userId) {
            return rows;
        }

        @Override
        public List<FinancialPositionAccountRow> findFinancialPositionAccountsAsOf(Long userId, LocalDate asOf) {
            return asOfRows;
        }
    }
}
