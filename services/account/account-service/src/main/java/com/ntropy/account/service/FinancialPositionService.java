package com.ntropy.account.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.api.dto.FinancialPositionSummary;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialPositionMapper;
import com.ntropy.account.mapper.projection.FinancialPositionAccountRow;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/** 사용자의 ACTIVE·KRW 계좌를 기준으로 재무진단용 금융자산·부채를 집계한다. */
@Service
@RequiredArgsConstructor
public class FinancialPositionService {

    private static final String DEPOSIT_TYPE_ORDINARY = "11";
    private static final String DEPOSIT_TYPE_SAVINGS = "12";
    private static final String DEPOSIT_TYPE_LOAN = "40";

    private final FinancialPositionMapper financialPositionMapper;

    @Transactional(readOnly = true)
    public FinancialPositionSummary findFinancialPosition(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "userId는 양수여야 합니다.");
        }

        List<FinancialPositionAccountRow> rows = financialPositionMapper.findFinancialPositionAccounts(userId);
        return aggregate(rows, false);
    }

    /**
     * {@code asOf} 기준일 이하 마지막 거래의 거래 후 잔액으로 복원한 과거 시점 금융자산·부채를 조회한다.
     * 기준일 이하 거래가 없거나 마지막 거래에 거래 후 잔액이 없는 계좌는 집계에서 제외한다.
     */
    @Transactional(readOnly = true)
    public FinancialPositionSummary findFinancialPosition(Long userId, LocalDate asOf) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "userId는 양수여야 합니다.");
        }
        if (asOf == null) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "asOf는 필수입니다.");
        }

        List<FinancialPositionAccountRow> rows =
                financialPositionMapper.findFinancialPositionAccountsAsOf(userId, asOf);
        return aggregate(rows, true);
    }

    private FinancialPositionSummary aggregate(
            List<FinancialPositionAccountRow> rows,
            boolean normalizeHistoricalLoanSign
    ) {
        long liquidAssets = 0L;
        long safeAssets = 0L;
        long totalLiabilities = 0L;

        for (FinancialPositionAccountRow row : rows) {
            String depositTypeCode = row.getDepositTypeCode();

            if (DEPOSIT_TYPE_ORDINARY.equals(depositTypeCode)) {
                if (Boolean.TRUE.equals(row.getOverdraftYn())) {
                    continue;
                }
                liquidAssets = addWon(liquidAssets, toWon(row, false));
            } else if (DEPOSIT_TYPE_SAVINGS.equals(depositTypeCode)) {
                safeAssets = addWon(safeAssets, toWon(row, false));
            } else if (DEPOSIT_TYPE_LOAN.equals(depositTypeCode) && row.getAccountGroup() == AccountGroup.LOAN) {
                // 현재 ACCOUNT.balance는 수집 시 이미 양수로 정규화되므로 음수면 무결성 오류다.
                // 과거 거래의 after_balance만 원문 부호가 남을 수 있어 과거 조회에서만 정규화한다.
                totalLiabilities = addWon(
                        totalLiabilities,
                        toWon(row, normalizeHistoricalLoanSign)
                );
            }
        }

        long totalFinancialAssets = addWon(liquidAssets, safeAssets);
        long netFinancialAssets = subtractWon(totalFinancialAssets, totalLiabilities);

        return new FinancialPositionSummary(
                liquidAssets, safeAssets, totalFinancialAssets, totalLiabilities, netFinancialAssets
        );
    }

    /**
     * DECIMAL(18,2) 잔액을 원 단위 Long으로 정확히 변환한다.
     * 소수부가 있거나 Long 범위를 초과하면 계산을 실패시킨다.
     *
     * @param normalizeSign true면 음수 잔액을 부호만 제거해 사용한다(대출 잔액 전용).
     *                      false면 기존과 동일하게 음수를 무결성 오류로 거부한다.
     */
    private static long toWon(FinancialPositionAccountRow row, boolean normalizeSign) {
        BigDecimal balance = row.getBalance();
        if (normalizeSign && balance != null && balance.signum() < 0) {
            balance = balance.abs();
        }
        if (balance == null || balance.signum() < 0) {
            throw new ServiceException(
                    AccountErrorCode.FINANCIAL_POSITION_BALANCE_INVALID, "accountId=" + row.getAccountId());
        }
        try {
            return balance.setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException e) {
            throw new ServiceException(
                    AccountErrorCode.FINANCIAL_POSITION_BALANCE_INVALID, "accountId=" + row.getAccountId());
        }
    }

    private static long addWon(long base, long amount) {
        try {
            return Math.addExact(base, amount);
        } catch (ArithmeticException e) {
            throw new ServiceException(AccountErrorCode.FINANCIAL_POSITION_OVERFLOW);
        }
    }

    private static long subtractWon(long base, long amount) {
        try {
            return Math.subtractExact(base, amount);
        } catch (ArithmeticException e) {
            throw new ServiceException(AccountErrorCode.FINANCIAL_POSITION_OVERFLOW);
        }
    }
}
