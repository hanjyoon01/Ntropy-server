package com.ntropy.account.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
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
import com.ntropy.account.api.dto.AccountRegistrationCommand;
import com.ntropy.account.port.ai.TransactionClassificationPort;
import com.ntropy.common.exception.ServiceException;

class LocalFinancialAccountCommandClientTest {

    @Test
    void registersVirtualAccountViaRegenerationService() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        StubVirtualAccountRegenerationService regenerationService = new StubVirtualAccountRegenerationService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, regenerationService, new StubAccountLifecycleMapper(1, 1),
                new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null, null)
        );

        assertEquals("VIRTUAL", result.connectionType());
        assertEquals(3, result.accountCount());
        assertEquals(42L, regenerationService.lastUserId);
        assertEquals(PersonalBank.SHINHAN_BANK, regenerationService.lastBank);
        assertTrue(collectionService.collectCalls == 0, "VIRTUAL 요청은 CODEF 수집을 호출하면 안 된다");
    }

    @Test
    void ignoresBirthDateForVirtualAccount() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null, "19900101")
        );

        assertEquals("VIRTUAL", result.connectionType());
        assertTrue(collectionService.collectCalls == 0, "VIRTUAL 요청은 CODEF 수집을 호출하면 안 된다");
    }

    @Test
    void requiresCredentialsOnlyForCodef() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("CODEF", "0088", null, null, null)
                )
        );

        assertEquals(400, exception.getStatusCode());
    }

    @Test
    void rejectsMissingBirthDateForRequiredBank() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id", "bank-password", null)
                )
        );

        assertEquals(AccountErrorCode.BIRTH_DATE_REQUIRED.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void rejectsInvalidBirthDateFormatForRequiredBank() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> client.registerAccount(
                        42L, new AccountRegistrationCommand(
                                "CODEF", "0003", "bank-id", "bank-password", "1990-01-01"
                        )
                )
        );

        assertEquals(AccountErrorCode.BIRTH_DATE_INVALID.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void registersKookminBankWithValidBirthDate() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id", "bank-password", "19900101")
        );

        assertEquals("CODEF", result.connectionType());
        assertEquals(1, collectionService.collectCalls);
        assertEquals("19900101", collectionService.lastBirthDate);
    }

    @Test
    void classifiesUsersTransactionsAfterCodefCollectionCompletes() {
        StubTransactionClassificationCommandClient classificationClient =
                new StubTransactionClassificationCommandClient();
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), new StubAccountCollectionService(),
                new StubVirtualAccountRegenerationService(), new StubVirtualFinancialDataService(),
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(false),
                new StubCodefConnectionMapper(), classificationClient
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );

        assertEquals(List.of(42L), classificationClient.userIds);
    }

    @Test
    void keepsAccountRegistrationSuccessfulWhenClassificationFails() {
        StubTransactionClassificationCommandClient classificationClient =
                new StubTransactionClassificationCommandClient();
        classificationClient.failure = new IllegalStateException("분류 실패");
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), new StubAccountCollectionService(),
                new StubVirtualAccountRegenerationService(), new StubVirtualFinancialDataService(),
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(false),
                new StubCodefConnectionMapper(), classificationClient
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("VIRTUAL", "0088", null, null, null)
        );

        assertEquals("VIRTUAL", result.connectionType());
        assertEquals(List.of(42L), classificationClient.userIds);
    }

    @Test
    void registersIndustrialBankWithValidBirthDate() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0003", "bank-id", "bank-password", "19900101")
        );

        assertEquals(1, collectionService.collectCalls);
        assertEquals("19900101", collectionService.lastBirthDate);
    }

    @Test
    void doesNotForwardBirthDateForBanksThatDoNotRequireIt() {
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                collectionService, new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", "19900101")
        );

        assertEquals(1, collectionService.collectCalls);
        assertEquals(null, collectionService.lastBirthDate, "생년월일이 필요 없는 은행에는 전달하지 않는다");
    }

    @Test
    void myDataStatusUsesOnlyCodefConnection() {
        StubCodefConnectionMapper mapper = new StubCodefConnectionMapper();
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 1), mapper
        );

        assertFalse(client.findMyDataStatus(42L).connected());

        CodefConnection connection = new CodefConnection();
        connection.setUserId(42L);
        connection.setProvider(ConnectionProvider.CODEF.name());
        connection.setConnectedId("secret-connected-id");
        connection.setRegisteredInstitutionKeys("[\"0088\"]");
        mapper.connection = connection;

        var status = client.findMyDataStatus(42L);
        assertTrue(status.connected());
        assertEquals(List.of("0088"), status.connectedBanks().stream()
                .map(bank -> bank.organizationCode()).toList());
    }

    @Test
    void returnsSameNotFoundForDeactivationFailure() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(0, 1), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class, () -> client.deactivateAccount(42L, 100L)
        );
        assertEquals(404, exception.getStatusCode());
    }

    @Test
    void activatesAccountByFlippingStatusWithoutRegeneration() {
        StubVirtualAccountRegenerationService regenerationService = new StubVirtualAccountRegenerationService();
        StubAccountLifecycleMapper lifecycleMapper = new StubAccountLifecycleMapper(1, 1);
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), regenerationService,
                lifecycleMapper, new StubCodefConnectionMapper()
        );

        client.activateAccount(42L, 100L);

        assertEquals(100L, lifecycleMapper.lastActivatedAccountId);
        assertEquals(42L, lifecycleMapper.lastActivatedUserId);
        assertEquals(null, regenerationService.lastUserId, "활성화는 재생성을 호출하면 안 된다");
    }

    @Test
    void returnsSameNotFoundForActivationFailure() {
        LocalFinancialAccountCommandClient client = newClient(
                new StubAccountCollectionService(), new StubVirtualAccountRegenerationService(),
                new StubAccountLifecycleMapper(1, 0), new StubCodefConnectionMapper()
        );

        ServiceException exception = assertThrows(
                ServiceException.class, () -> client.activateAccount(42L, 100L)
        );
        assertEquals(404, exception.getStatusCode());
    }

    // --- 이슈 #205: 실 사용자 CODEF 연동 시 NTROPY 가상 데이터셋 자동 생성 ---

    @Test
    void generatesVirtualDatasetWhenUserHasNoneYet() {
        StubAccountMapper accountMapper = new StubAccountMapper(false);
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), new StubAccountCollectionService(),
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), accountMapper, new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );

        assertEquals(1, virtualFinancialDataService.generateCalls);
        assertEquals(42L, virtualFinancialDataService.lastUserId);
        assertEquals(PersonalBank.SHINHAN_BANK, virtualFinancialDataService.lastBank);
        assertEquals(ConnectionProvider.NTROPY.name(), accountMapper.lastProvider);
    }

    @Test
    void skipsVirtualDatasetGenerationWhenAlreadyExists() {
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), new StubAccountCollectionService(),
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(true), new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );

        assertEquals(0, virtualFinancialDataService.generateCalls,
                "이미 NTROPY 계좌가 있으면(비활성 포함) 다시 생성하지 않는다");
    }

    @Test
    void doesNotAttemptVirtualDatasetWhenCodefRegistrationFails() {
        StubPersonalBankAccountService personalBankAccountService = new StubPersonalBankAccountService();
        personalBankAccountService.failure = new IllegalStateException("CODEF 등록 실패");
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                personalBankAccountService, collectionService,
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(false), new StubCodefConnectionMapper()
        );

        assertThrows(IllegalStateException.class, () -> client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        ));

        assertEquals(0, virtualFinancialDataService.generateCalls, "은행 연결 등록 자체가 실패하면 가상 데이터는 생성하지 않는다");
        assertEquals(0, collectionService.collectCalls, "은행 연결 등록이 실패하면 실제 거래 수집도 실행되지 않는다");
    }

    @Test
    void collectsRealDataEvenWhenVirtualDatasetGenerationFails() {
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        virtualFinancialDataService.failure = new IllegalStateException("가상 데이터 생성 실패");
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), collectionService,
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(false), new StubCodefConnectionMapper()
        );

        var result = client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );

        assertEquals("CODEF", result.connectionType(), "가상 데이터 생성 실패가 실제 계좌 등록 응답을 막으면 안 된다");
        assertEquals(1, collectionService.collectCalls, "가상 데이터 생성이 실패해도 실제 거래 수집은 계속 실행된다");
    }

    @Test
    void attemptsVirtualDatasetGenerationBeforePropagatingCollectionFailure() {
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        StubAccountCollectionService collectionService = new StubAccountCollectionService();
        collectionService.failure = new IllegalStateException("CODEF 거래 수집 실패");
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), collectionService,
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(false), new StubCodefConnectionMapper()
        );

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        ));

        assertEquals("CODEF 거래 수집 실패", exception.getMessage());
        assertEquals(1, virtualFinancialDataService.generateCalls,
                "실제 거래 수집이 실패하더라도 가상 데이터 생성은 먼저 시도되어야 한다");
    }

    @Test
    void attemptsVirtualDatasetGenerationBetweenRegistrationAndCollection() {
        List<String> callOrder = new ArrayList<>();
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(callOrder), new StubAccountCollectionService(callOrder),
                new StubVirtualAccountRegenerationService(), new StubVirtualFinancialDataService(callOrder),
                new StubAccountLifecycleMapper(1, 1), new StubAccountMapper(false), new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );

        assertEquals(List.of("register", "virtual", "collect"), callOrder);
    }

    @Test
    void retriesVirtualDatasetGenerationOnNextBankWhenStillAbsent() {
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        virtualFinancialDataService.failure = new IllegalStateException("첫 시도 실패");
        StubAccountMapper accountMapper = new StubAccountMapper(false);
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), new StubAccountCollectionService(),
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), accountMapper, new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );
        assertEquals(1, virtualFinancialDataService.generateCalls);
        assertEquals(PersonalBank.SHINHAN_BANK, virtualFinancialDataService.lastBank);

        virtualFinancialDataService.failure = null;
        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id-2", "bank-password-2", "19900101")
        );

        assertEquals(2, virtualFinancialDataService.generateCalls,
                "NTROPY 계좌가 여전히 없으면 다음 은행 연동에서 다시 시도한다");
        assertEquals(PersonalBank.KB_KOOKMIN_BANK, virtualFinancialDataService.lastBank,
                "재시도는 최초 은행이 아니라 현재 연동 중인 은행 기준으로 이뤄진다");
    }

    @Test
    void doesNotRegenerateVirtualDatasetWhenLinkingSecondBank() {
        StubVirtualFinancialDataService virtualFinancialDataService = new StubVirtualFinancialDataService();
        StubAccountMapper accountMapper = new StubAccountMapper(false);
        LocalFinancialAccountCommandClient client = newClient(
                new StubPersonalBankAccountService(), new StubAccountCollectionService(),
                new StubVirtualAccountRegenerationService(), virtualFinancialDataService,
                new StubAccountLifecycleMapper(1, 1), accountMapper, new StubCodefConnectionMapper()
        );

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0088", "bank-id", "bank-password", null)
        );
        accountMapper.existsAny = true;

        client.registerAccount(
                42L, new AccountRegistrationCommand("CODEF", "0004", "bank-id-2", "bank-password-2", "19900101")
        );

        assertEquals(1, virtualFinancialDataService.generateCalls,
                "두 번째 은행 연동은 기존 가상계좌와 거래를 재생성하면 안 된다");
    }

    private static LocalFinancialAccountCommandClient newClient(
            AccountCollectionService collectionService,
            VirtualAccountRegenerationService regenerationService,
            AccountLifecycleMapper lifecycleMapper,
            CodefConnectionMapper connectionMapper
    ) {
        return newClient(
                new StubPersonalBankAccountService(), collectionService, regenerationService,
                new StubVirtualFinancialDataService(), lifecycleMapper,
                new StubAccountMapper(false), connectionMapper
        );
    }

    private static LocalFinancialAccountCommandClient newClient(
            PersonalBankAccountService personalBankAccountService,
            AccountCollectionService collectionService,
            VirtualAccountRegenerationService regenerationService,
            VirtualFinancialDataService virtualFinancialDataService,
            AccountLifecycleMapper lifecycleMapper,
            AccountMapper accountMapper,
            CodefConnectionMapper connectionMapper
    ) {
        return newClient(
                personalBankAccountService, collectionService, regenerationService,
                virtualFinancialDataService, lifecycleMapper, accountMapper, connectionMapper,
                new StubTransactionClassificationCommandClient()
        );
    }

    private static LocalFinancialAccountCommandClient newClient(
            PersonalBankAccountService personalBankAccountService,
            AccountCollectionService collectionService,
            VirtualAccountRegenerationService regenerationService,
            VirtualFinancialDataService virtualFinancialDataService,
            AccountLifecycleMapper lifecycleMapper,
            AccountMapper accountMapper,
            CodefConnectionMapper connectionMapper,
            TransactionClassificationPort classificationClient
    ) {
        return new LocalFinancialAccountCommandClient(
                personalBankAccountService, collectionService, regenerationService, virtualFinancialDataService,
                lifecycleMapper, accountMapper, connectionMapper, classificationClient
        );
    }

    private static class StubTransactionClassificationCommandClient
            implements TransactionClassificationPort {
        private final List<Long> userIds = new ArrayList<>();
        private RuntimeException failure;

        @Override
        public int classifyUnanalyzedTransactions(Long userId) {
            userIds.add(userId);
            if (failure != null) {
                throw failure;
            }
            return 0;
        }
    }

    private static class StubPersonalBankAccountService extends PersonalBankAccountService {
        private final List<String> callOrder;
        private RuntimeException failure;

        StubPersonalBankAccountService() {
            this(null);
        }

        StubPersonalBankAccountService(List<String> callOrder) {
            super(null, null, null);
            this.callOrder = callOrder;
        }

        @Override
        public CodefConnection registerPersonalAccount(Long userId, PersonalBank bank, String loginId,
                                                        String rawPassword, String birthDate) {
            if (callOrder != null) {
                callOrder.add("register");
            }
            if (failure != null) {
                throw failure;
            }
            return new CodefConnection();
        }
    }

    private static class StubAccountCollectionService extends AccountCollectionService {
        private int collectCalls = 0;
        private String lastBirthDate;
        private final List<String> callOrder;
        private RuntimeException failure;

        StubAccountCollectionService() {
            this(null);
        }

        StubAccountCollectionService(List<String> callOrder) {
            super(null, null, null, null, null, null, null);
            this.callOrder = callOrder;
        }

        @Override
        public List<Account> collect(Long userId, PersonalBank bank, String birthDate,
                                     LocalDate transactionStartDate, LocalDate transactionEndDate) {
            collectCalls++;
            lastBirthDate = birthDate;
            if (callOrder != null) {
                callOrder.add("collect");
            }
            if (failure != null) {
                throw failure;
            }
            return List.of(new Account());
        }
    }

    private static class StubVirtualFinancialDataService extends VirtualFinancialDataService {
        private int generateCalls = 0;
        private Long lastUserId;
        private PersonalBank lastBank;
        private final List<String> callOrder;
        private RuntimeException failure;

        StubVirtualFinancialDataService() {
            this(null);
        }

        StubVirtualFinancialDataService(List<String> callOrder) {
            super(null, null, null, null, null, null);
            this.callOrder = callOrder;
        }

        @Override
        public GenerationSummary generateForUser(Long userId, PersonalBank bank) {
            generateCalls++;
            lastUserId = userId;
            lastBank = bank;
            if (callOrder != null) {
                callOrder.add("virtual");
            }
            if (failure != null) {
                throw failure;
            }
            return new GenerationSummary(1, 3, 2, 306, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        }
    }

    private static class StubAccountMapper implements AccountMapper {
        private boolean existsAny;
        private Long lastUserId;
        private String lastProvider;

        StubAccountMapper(boolean existsAny) {
            this.existsAny = existsAny;
        }

        @Override
        public void upsert(Account account) {
        }

        @Override
        public void updateAccountDetails(Account account) {
        }

        @Override
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return null;
        }

        @Override
        public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) {
            return null;
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            return List.of();
        }

        @Override
        public boolean existsAnyByUserIdAndProvider(Long userId, String provider) {
            lastUserId = userId;
            lastProvider = provider;
            return existsAny;
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
        }
    }

    private static class StubVirtualAccountRegenerationService extends VirtualAccountRegenerationService {
        private Long lastUserId;
        private PersonalBank lastBank;

        StubVirtualAccountRegenerationService() {
            super(null, null, null);
        }

        @Override
        public GenerationSummary regenerateForUser(Long userId, PersonalBank bank) {
            lastUserId = userId;
            lastBank = bank;
            return new GenerationSummary(1, 3, 2, 306, LocalDate.of(2026, 4, 1), LocalDate.of(2026, 6, 30));
        }
    }

    private static class StubAccountLifecycleMapper implements AccountLifecycleMapper {
        private final int deactivateResult;
        private final int activateResult;
        private Long lastActivatedAccountId;
        private Long lastActivatedUserId;

        StubAccountLifecycleMapper(int deactivateResult, int activateResult) {
            this.deactivateResult = deactivateResult;
            this.activateResult = activateResult;
        }

        @Override
        public int deactivateByIdAndUserId(Long id, Long userId) {
            return deactivateResult;
        }

        @Override
        public int activateByIdAndUserId(Long id, Long userId) {
            lastActivatedAccountId = id;
            lastActivatedUserId = userId;
            return activateResult;
        }
    }

    private static class StubCodefConnectionMapper implements CodefConnectionMapper {
        private CodefConnection connection;
        private int insertCalls = 0;

        @Override
        public void insert(CodefConnection codefConnection) {
            insertCalls++;
        }

        @Override
        public void insertIfAbsent(CodefConnection codefConnection) {
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            if (connection == null || !userId.equals(connection.getUserId())
                    || !provider.equals(connection.getProvider())) {
                return null;
            }
            return connection;
        }
    }
}
