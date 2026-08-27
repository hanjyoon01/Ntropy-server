package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.AccountQueryClient;
import com.ntropy.account.api.dto.AccountSummary;
import com.ntropy.account.api.dto.AccountTransactionSummary;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.FinancialDataQueryMapper;
import com.ntropy.account.mapper.projection.OwnedAccountTransactionRow;
import com.ntropy.account.mapper.projection.OwnedTransactionCountRow;
import com.ntropy.common.dto.account.PageSummary;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalAccountQueryClient implements AccountQueryClient {

    private final FinancialDataQueryMapper financialDataQueryMapper;

    @Override
    public List<AccountSummary> findAccounts(Long userId) {
        requirePositive(userId, "userId");
        return financialDataQueryMapper.findAccountsByUserId(userId).stream()
                .map(LocalAccountQueryClient::toSummary)
                .toList();
    }

    @Override
    public AccountSummary findAccount(Long userId, Long accountId) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");

        Account account = financialDataQueryMapper.findAccountByIdAndUserId(accountId, userId);
        if (account == null) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
        return toSummary(account);
    }

    @Override
    public PageSummary<AccountTransactionSummary> findTransactions(Long userId, Long accountId,
                                                                    LocalDate startDate, LocalDate endDate,
                                                                    int page, int size) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");
        requirePage(page, size);
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "startDate는 endDate보다 늦을 수 없습니다.");
        }

        OwnedTransactionCountRow count = financialDataQueryMapper.countTransactionsByAccountIdAndUserId(
                accountId, userId, startDate, endDate
        );
        if (count == null) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }

        long offset = (long) page * size;
        if (count.getTotalElements() == 0 || offset >= count.getTotalElements()) {
            return PageSummary.of(List.of(), page, size, count.getTotalElements());
        }
        if (offset > Integer.MAX_VALUE) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "요청 가능한 페이지 범위를 초과했습니다.");
        }

        List<AccountTransactionSummary> content = financialDataQueryMapper.findTransactionsByAccountIdAndUserId(
                        accountId, userId, startDate, endDate, size, (int) offset
                ).stream()
                .map(LocalAccountQueryClient::toSummary)
                .toList();
        return PageSummary.of(content, page, size, count.getTotalElements());
    }

    private static AccountSummary toSummary(Account account) {
        return new AccountSummary(
                account.getId(), connectionType(account.getConnectionProvider()), account.getOrganizationCode(),
                bankName(account.getOrganizationCode()), account.getAccountGroup().name(),
                account.getDepositTypeCode(), account.getAccountNoMasked(), account.getAccountName(),
                account.getBalance(), account.getLoanContractPrincipal(), account.getInterestRate(),
                account.getCurrencyCode(), account.getAccountStartDate(), account.getMaturityDate(),
                account.getLastTranDate(), account.getOverdraftYn(), account.getNextPaymentDate(),
                account.getStatus() == null ? "ACTIVE" : account.getStatus().name()
        );
    }

    private static AccountTransactionSummary toSummary(OwnedAccountTransactionRow row) {
        return new AccountTransactionSummary(
                row.getTransactionId(), row.getOwnedAccountId(), row.getTransactionCategory().name(),
                row.getTransactionDate(), row.getTransactionTime(), row.getOutAmount(), row.getInAmount(),
                row.getLoanTransactionTypeName(), row.getLoanPrincipalAmount(), row.getLoanInterestAmount(),
                row.getAfterBalance(), row.getDesc1(), row.getDesc2(), row.getDesc3(), row.getDesc4()
        );
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, fieldName + "는 양수여야 합니다.");
        }
    }

    private static void requirePage(int page, int size) {
        if (page < 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "page는 0 이상이어야 합니다.");
        }
        if (size < 1 || size > 100) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "size는 1 이상 100 이하여야 합니다.");
        }
    }

    private static String connectionType(String provider) {
        return ConnectionProvider.NTROPY.name().equals(provider) ? "VIRTUAL" : "CODEF";
    }

    private static String bankName(String organizationCode) {
        try {
            return PersonalBank.fromOrganizationCode(organizationCode).getDisplayName();
        } catch (IllegalArgumentException ignored) {
            return organizationCode;
        }
    }
}
