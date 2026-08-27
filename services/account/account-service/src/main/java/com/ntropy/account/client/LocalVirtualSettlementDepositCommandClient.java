package com.ntropy.account.client;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.api.client.VirtualSettlementDepositCommandClient;
import com.ntropy.account.api.dto.VirtualSettlementDepositCommand;
import com.ntropy.account.api.dto.VirtualSettlementDepositResult;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.TransactionFingerprint;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.VirtualSettlementDepositMapper;

import lombok.RequiredArgsConstructor;

/** 플랫폼 예상 정산금을 사용자의 NTROPY 가상 수시입출금 계좌에 기록한다. */
@Component
@RequiredArgsConstructor
public class LocalVirtualSettlementDepositCommandClient implements VirtualSettlementDepositCommandClient {

    private static final String ORDINARY_DEPOSIT_TYPE_CODE = "11";
    private static final String FINGERPRINT_TYPE = "VIRTUAL_PLATFORM_SETTLEMENT";

    private final AccountMapper accountMapper;
    private final VirtualSettlementDepositMapper depositMapper;

    @Override
    @Transactional
    public VirtualSettlementDepositResult createOrAdjust(VirtualSettlementDepositCommand command) {
        validate(command);
        Account account = findVirtualOrdinaryAccount(command.userId());
        if (account == null) {
            return VirtualSettlementDepositResult.unavailable();
        }

        String settlementKey = "VS:" + TransactionFingerprint.hash(
                command.userId(),
                command.platformId(),
                command.periodStart(),
                command.periodEnd(),
                command.depositDate()
        );
        BigDecimal lockedBalance = depositMapper.findBalanceForUpdate(account.getId());
        BigDecimal currentBalance = lockedBalance == null ? BigDecimal.ZERO : lockedBalance;
        BigDecimal generatedAmount = depositMapper.sumGeneratedAmount(account.getId(), settlementKey);
        if (generatedAmount == null) {
            generatedAmount = BigDecimal.ZERO;
        }
        BigDecimal amount = BigDecimal.valueOf(command.amount()).subtract(generatedAmount);
        if (amount.signum() <= 0) {
            return VirtualSettlementDepositResult.alreadyAvailable();
        }

        AccountTransaction transaction = new AccountTransaction();
        transaction.setAccountId(account.getId());
        transaction.setFingerprint(TransactionFingerprint.hash(
                FINGERPRINT_TYPE,
                settlementKey,
                command.amount()
        ));
        transaction.setTransactionCategory(AccountTransactionCategory.ORDINARY);
        transaction.setTranDate(command.depositDate());
        transaction.setTranTime(LocalTime.of(6, Math.floorMod(command.platformId().intValue(), 60)));
        transaction.setOutAmount(BigDecimal.ZERO);
        transaction.setInAmount(amount);
        transaction.setAfterBalance(currentBalance.add(amount));
        // IncomingCounterpartyNameExtractor가 IBK는 desc1, 나머지 은행은 desc3을 사용한다.
        transaction.setDesc1(command.depositName());
        transaction.setDesc2("정산입금");
        transaction.setDesc3(command.depositName());
        transaction.setDesc4(settlementKey);

        int inserted = depositMapper.insertIfAbsent(transaction);
        if (inserted != 1) {
            return VirtualSettlementDepositResult.alreadyAvailable();
        }
        if (depositMapper.incrementBalanceAndAdvanceLastTranDate(
                account.getId(), amount, command.depositDate()) != 1) {
            throw new IllegalStateException("가상 정산 입금 계좌 잔액 갱신에 실패했습니다: accountId=" + account.getId());
        }
        return VirtualSettlementDepositResult.created();
    }

    private Account findVirtualOrdinaryAccount(Long userId) {
        List<Account> accounts = accountMapper.findByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name());
        if (accounts == null) {
            return null;
        }
        return accounts.stream()
                .filter(account -> account.getAccountGroup() == AccountGroup.DEPOSIT_TRUST)
                .filter(account -> ORDINARY_DEPOSIT_TYPE_CODE.equals(account.getDepositTypeCode()))
                .findFirst()
                .orElse(null);
    }

    private static void validate(VirtualSettlementDepositCommand command) {
        if (command == null
                || command.userId() == null
                || command.platformId() == null
                || command.periodStart() == null
                || command.periodEnd() == null
                || command.depositDate() == null
                || command.amount() == null
                || command.amount() <= 0
                || command.depositName() == null
                || command.depositName().isBlank()) {
            throw new IllegalArgumentException("유효한 가상 정산 입금 명령이 필요합니다");
        }
        if (command.periodStart().isAfter(command.periodEnd())) {
            throw new IllegalArgumentException("정산기간 시작일은 종료일 이후일 수 없습니다");
        }
    }
}
