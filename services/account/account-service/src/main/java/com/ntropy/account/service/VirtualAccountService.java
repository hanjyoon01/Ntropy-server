package com.ntropy.account.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountNoHash;
import com.ntropy.account.domain.AccountNoMask;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;

/**
 * 실제 CODEF 계정 등록 없이 MVP용 가상계좌를 생성/조회한다 (이슈 #35).
 * 가상 연결은 은행 로그인 정보가 필요하지 않다.
 */
@Service
@RequiredArgsConstructor
public class VirtualAccountService {

    private static final String DEFAULT_DEPOSIT_TYPE_CODE = "11";
    private static final String CURRENCY_KRW = "KRW";
    private static final String DATASET_VERSION = "v1";
    private static final long ACCOUNT_NUMBER_BOUND = 1_000_000_000_000L;

    private final VirtualConnectionService virtualConnectionService;
    private final AccountMapper accountMapper;

    @Transactional
    public Account createVirtualAccount(Long userId, String organizationCode) {
        requireUserId(userId);
        PersonalBank bank = resolveBank(organizationCode);

        CodefConnection connection = virtualConnectionService.getOrCreateConnection(userId);
        virtualConnectionService.registerInstitution(connection, bank.getOrganizationCode());

        Account account = buildVirtualAccount(userId, connection, bank);
        accountMapper.upsert(account);
        Account saved = accountMapper.findByConnectionIdAndAccountNoHash(
                connection.getId(), account.getAccountNoHash()
        );
        if (saved == null) {
            throw new IllegalStateException("가상계좌 저장 확인 실패");
        }
        return saved;
    }

    public List<Account> listAccounts(Long userId) {
        requireUserId(userId);
        return accountMapper.findByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name());
    }

    public Account getAccountDetail(Long userId, Long accountId) {
        requireUserId(userId);
        Account account = accountMapper.findByIdAndUserIdAndProvider(
                accountId, userId, ConnectionProvider.NTROPY.name()
        );
        if (account == null) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
        return account;
    }

    private static Account buildVirtualAccount(Long userId, CodefConnection connection, PersonalBank bank) {
        String rawAccountNo = generateRawAccountNo(userId, bank.getOrganizationCode(), connection.getConnectedId());

        Account account = new Account();
        account.setCodefConnectionId(connection.getId());
        account.setUserId(userId);
        account.setOrganizationCode(bank.getOrganizationCode());
        account.setAccountGroup(AccountGroup.DEPOSIT_TRUST);
        account.setDepositTypeCode(DEFAULT_DEPOSIT_TYPE_CODE);
        account.setAccountNoMasked(AccountNoMask.mask(rawAccountNo));
        account.setAccountNoHash(AccountNoHash.hash(bank.getOrganizationCode(), rawAccountNo));
        account.setAccountName(bank.getDisplayName() + " 가상계좌");
        account.setBalance(BigDecimal.ZERO);
        account.setCurrencyCode(CURRENCY_KRW);
        return account;
    }

    private static String generateRawAccountNo(Long userId, String organizationCode, String connectedId) {
        String seed = userId + ":" + organizationCode + ":" + connectedId + ":" + DATASET_VERSION;
        String fingerprint = AccountNoHash.hash(organizationCode, seed);
        long numericValue = Long.parseLong(fingerprint.substring(0, 15), 16) % ACCOUNT_NUMBER_BOUND;
        return String.format(Locale.ROOT, "%012d", numericValue);
    }

    private static PersonalBank resolveBank(String organizationCode) {
        try {
            return PersonalBank.fromOrganizationCode(organizationCode);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(AccountErrorCode.UNSUPPORTED_BANK, organizationCode);
        }
    }

    private static void requireUserId(Long userId) {
        if (userId == null || userId <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "사용자 ID는 양수여야 합니다.");
        }
    }

}
