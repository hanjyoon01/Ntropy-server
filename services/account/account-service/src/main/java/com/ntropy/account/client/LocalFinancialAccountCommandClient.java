package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.FinancialAccountCommandClient;
import com.ntropy.account.api.dto.AccountRegistrationCommand;
import com.ntropy.account.api.dto.AccountRegistrationSummary;
import com.ntropy.account.api.dto.BankSummary;
import com.ntropy.account.api.dto.MyDataConnectionSummary;
import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.AccountLifecycleMapper;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.service.AccountCollectionService;
import com.ntropy.account.service.PersonalBankAccountService;
import com.ntropy.account.service.VirtualAccountRegenerationService;
import com.ntropy.account.service.VirtualFinancialDataService;
import com.ntropy.account.service.VirtualFinancialDataService.GenerationSummary;
import com.ntropy.account.port.ai.TransactionClassificationPort;
import com.ntropy.common.exception.ServiceException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** BFF에 노출할 금융계좌 명령과 연결 상태를 account-service 기능으로 조합한다. */
@Component
@RequiredArgsConstructor
@Slf4j
public class LocalFinancialAccountCommandClient implements FinancialAccountCommandClient {

    private static final int DEFAULT_COLLECTION_DAYS = 90;

    private final PersonalBankAccountService personalBankAccountService;
    private final AccountCollectionService accountCollectionService;
    private final VirtualAccountRegenerationService virtualAccountRegenerationService;
    private final VirtualFinancialDataService virtualFinancialDataService;
    private final AccountLifecycleMapper accountLifecycleMapper;
    private final AccountMapper accountMapper;
    private final CodefConnectionMapper codefConnectionMapper;
    private final TransactionClassificationPort transactionClassificationPort;

    @Override
    public List<BankSummary> findSupportedBanks() {
        return Arrays.stream(PersonalBank.values())
                .map(LocalFinancialAccountCommandClient::toBankSummary)
                .toList();
    }

    @Override
    public MyDataConnectionSummary findMyDataStatus(Long userId) {
        requirePositive(userId, "userId");
        CodefConnection connection = codefConnectionMapper.findByUserIdAndProvider(
                userId, ConnectionProvider.CODEF.name()
        );
        if (connection == null || connection.getConnectedId() == null || connection.getConnectedId().isBlank()) {
            return new MyDataConnectionSummary(false, List.of(), null);
        }

        List<BankSummary> connectedBanks = InstitutionKeys.parse(connection.getRegisteredInstitutionKeys()).stream()
                .map(LocalFinancialAccountCommandClient::resolveBank)
                .map(LocalFinancialAccountCommandClient::toBankSummary)
                .toList();
        return new MyDataConnectionSummary(true, connectedBanks, connection.getUpdatedAt());
    }

    @Override
    public AccountRegistrationSummary registerAccount(Long userId, AccountRegistrationCommand command) {
        requirePositive(userId, "userId");
        if (command == null) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "요청 본문이 필요합니다.");
        }
        PersonalBank bank = resolveBank(command.organizationCode());
        String connectionType = normalizeConnectionType(command.connectionType());

        if ("VIRTUAL".equals(connectionType)) {
            GenerationSummary summary = virtualAccountRegenerationService.regenerateForUser(userId, bank);
            classifyTransactionsSafely(userId);
            return new AccountRegistrationSummary(connectionType, bank.getOrganizationCode(), summary.accounts());
        }

        requireNonBlank(command.bankLoginId(), bank.getDisplayName() + " 로그인 ID");
        requireNonBlank(command.bankLoginPassword(), bank.getDisplayName() + " 로그인 비밀번호");
        String birthDate = resolveBirthDate(bank, command.birthDate());
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(DEFAULT_COLLECTION_DAYS - 1L);

        personalBankAccountService.registerPersonalAccount(
                userId, bank, command.bankLoginId(), command.bankLoginPassword(), birthDate
        );
        ensureVirtualDatasetSafely(userId, bank);
        int accountCount = accountCollectionService.collect(userId, bank, birthDate, startDate, endDate).size();
        classifyTransactionsSafely(userId);
        return new AccountRegistrationSummary(connectionType, bank.getOrganizationCode(), accountCount);
    }

    private void classifyTransactionsSafely(Long userId) {
        try {
            int processed = transactionClassificationPort.classifyUnanalyzedTransactions(userId);
            log.info("계좌 연동 후 소비 분류 완료: userId={}, processed={}", userId, processed);
        } catch (RuntimeException e) {
            log.warn("계좌 연동 후 소비 분류 실패: userId={}", userId, e);
        }
    }

    private void ensureVirtualDatasetSafely(Long userId, PersonalBank bank) {
        try {
            if (accountMapper.existsAnyByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name())) {
                return;
            }
            virtualFinancialDataService.generateForUser(userId, bank);
        } catch (RuntimeException e) {
            log.warn(
                    "CODEF 연동 후 가상 금융 데이터셋 생성 실패: userId={}, organizationCode={}",
                    userId, bank.getOrganizationCode(), e
            );
        }
    }

    @Override
    public void deactivateAccount(Long userId, Long accountId) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");
        if (accountLifecycleMapper.deactivateByIdAndUserId(accountId, userId) == 0) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    @Override
    public void activateAccount(Long userId, Long accountId) {
        requirePositive(userId, "userId");
        requirePositive(accountId, "accountId");
        if (accountLifecycleMapper.activateByIdAndUserId(accountId, userId) == 0) {
            throw new ServiceException(AccountErrorCode.ACCOUNT_NOT_FOUND);
        }
    }

    private String resolveBirthDate(PersonalBank bank, String birthDate) {
        if (!bank.isBirthDateRequired()) {
            return null;
        }
        if (birthDate == null || birthDate.isBlank()) {
            throw new ServiceException(
                    AccountErrorCode.BIRTH_DATE_REQUIRED,
                    bank.getDisplayName() + " 생년월일이 필요합니다."
            );
        }
        try {
            return bank.normalizeBirthDate(birthDate);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(
                    AccountErrorCode.BIRTH_DATE_INVALID,
                    "YYYYMMDD 형식의 유효한 생년월일이 필요합니다."
            );
        }
    }

    private static String normalizeConnectionType(String connectionType) {
        if (connectionType == null) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, "connectionType이 필요합니다.");
        }
        String normalized = connectionType.trim().toUpperCase(java.util.Locale.ROOT);
        if (!"VIRTUAL".equals(normalized) && !"CODEF".equals(normalized)) {
            throw new ServiceException(
                    AccountErrorCode.INVALID_REQUEST,
                    "connectionType은 VIRTUAL 또는 CODEF여야 합니다."
            );
        }
        return normalized;
    }

    private static PersonalBank resolveBank(String organizationCode) {
        try {
            return PersonalBank.fromOrganizationCode(organizationCode);
        } catch (IllegalArgumentException e) {
            throw new ServiceException(AccountErrorCode.UNSUPPORTED_BANK, organizationCode);
        }
    }

    private static BankSummary toBankSummary(PersonalBank bank) {
        return new BankSummary(
                bank.getOrganizationCode(), bank.getDisplayName(), bank.isBirthDateRequired(),
                true, true, bank != PersonalBank.SC_BANK
        );
    }

    private static void requirePositive(Long value, String fieldName) {
        if (value == null || value <= 0) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, fieldName + "는 양수여야 합니다.");
        }
    }

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new ServiceException(AccountErrorCode.INVALID_REQUEST, fieldName + "가 필요합니다.");
        }
    }
}
