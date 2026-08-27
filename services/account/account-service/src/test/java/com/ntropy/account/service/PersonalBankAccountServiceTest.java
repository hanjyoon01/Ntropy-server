package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ntropy.account.client.codef.CodefBankAccountClient;
import com.ntropy.account.client.codef.CodefConnectionClient;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

import static com.ntropy.account.service.CodefConnectionServiceTest.testBirthDateCipher;

class PersonalBankAccountServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void registersSelectedBankAndGetsPersonalAccountList() throws Exception {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnectionService connectionService =
                new CodefConnectionService(connectionClient, mapper, testBirthDateCipher());
        StubCodefBankAccountClient bankAccountClient = new StubCodefBankAccountClient(
                objectMapper.readTree(
                        "{\"result\":{\"code\":\"CF-00000\"},\"data\":{\"resDepositTrust\":[]}}"
                )
        );
        PersonalBankAccountService service = new PersonalBankAccountService(
                connectionService, mapper, bankAccountClient
        );

        JsonNode response = service.registerAndGetPersonalAccountList(
                1L,
                PersonalBank.SHINHAN_BANK,
                "shinhan-user",
                "shinhan-password",
                null
        );

        assertEquals("0088", connectionClient.organizationCode);
        assertEquals("BK", connectionClient.businessType);
        assertEquals("P", connectionClient.clientType);
        assertEquals("shinhan-user", connectionClient.loginId);
        assertEquals("shinhan-password", connectionClient.rawPassword);
        assertNull(connectionClient.birthDate);
        assertEquals("0088", bankAccountClient.organizationCode);
        assertEquals("connected-id", bankAccountClient.connectedId);
        assertEquals("CF-00000", response.path("result").path("code").asText());
    }

    @Test
    void sendsBirthDateForBanksThatRequireIt() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        PersonalBankAccountService service = new PersonalBankAccountService(
                new CodefConnectionService(connectionClient, mapper, testBirthDateCipher()), mapper, null
        );

        service.registerPersonalAccount(
                1L, PersonalBank.KB_KOOKMIN_BANK, "login-id", "password", "19900101"
        );

        assertEquals("0004", connectionClient.organizationCode);
        assertEquals("19900101", connectionClient.birthDate);
    }

    @Test
    void rejectsMissingOrInvalidBirthDateForRequiredBanks() {
        PersonalBankAccountService service = new PersonalBankAccountService(null, null, null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerPersonalAccount(
                        1L, PersonalBank.IBK_INDUSTRIAL_BANK, "id", "password", null
                )
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> service.registerPersonalAccount(
                        1L, PersonalBank.KB_KOOKMIN_BANK, "id", "password", "19900231"
                )
        );
    }

    @Test
    void omitsBirthDateForBanksThatDoNotUseIt() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        PersonalBankAccountService service = new PersonalBankAccountService(
                new CodefConnectionService(connectionClient, mapper, testBirthDateCipher()), mapper, null
        );

        service.registerPersonalAccount(
                1L, PersonalBank.SHINHAN_BANK, "id", "password", "19900101"
        );

        assertNull(connectionClient.birthDate);
    }

    @Test
    void rejectsAccountListWhenConnectionDoesNotExist() {
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        PersonalBankAccountService service = new PersonalBankAccountService(
                null, mapper, new StubCodefBankAccountClient(null)
        );

        assertThrows(
                IllegalStateException.class,
                () -> service.getPersonalAccountList(1L, PersonalBank.SHINHAN_BANK)
        );
    }

    private static class StubCodefConnectionClient extends CodefConnectionClient {

        private String organizationCode;
        private String businessType;
        private String clientType;
        private String loginId;
        private String rawPassword;
        private String birthDate;

        StubCodefConnectionClient() {
            super(null, null);
        }

        @Override
        public String createConnection(String organizationCode, String businessType, String clientType,
                                       String loginId, String rawPassword, String birthDate) {
            capture(organizationCode, businessType, clientType, loginId, rawPassword, birthDate);
            return "connected-id";
        }

        @Override
        public void addConnection(String connectedId, String organizationCode,
                                  String businessType, String clientType,
                                  String loginId, String rawPassword, String birthDate) {
            capture(organizationCode, businessType, clientType, loginId, rawPassword, birthDate);
        }

        @Override
        public void updateConnection(String connectedId, String organizationCode,
                                     String businessType, String clientType,
                                     String loginId, String rawPassword, String birthDate) {
            capture(organizationCode, businessType, clientType, loginId, rawPassword, birthDate);
        }

        private void capture(String organizationCode, String businessType, String clientType,
                             String loginId, String rawPassword, String birthDate) {
            this.organizationCode = organizationCode;
            this.businessType = businessType;
            this.clientType = clientType;
            this.loginId = loginId;
            this.rawPassword = rawPassword;
            this.birthDate = birthDate;
        }
    }

    private static class StubCodefBankAccountClient extends CodefBankAccountClient {

        private final JsonNode response;
        private String organizationCode;
        private String connectedId;

        StubCodefBankAccountClient(JsonNode response) {
            super(null);
            this.response = response;
        }

        @Override
        public JsonNode getPersonalAccountList(String organizationCode, String connectedId) {
            this.organizationCode = organizationCode;
            this.connectedId = connectedId;
            return response;
        }
    }

    private static class InMemoryCodefConnectionMapper implements CodefConnectionMapper {

        private CodefConnection connection;

        @Override
        public void insert(CodefConnection codefConnection) {
            this.connection = codefConnection;
        }

        @Override
        public void insertIfAbsent(CodefConnection codefConnection) {
            if (this.connection == null) {
                insert(codefConnection);
            }
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
            this.connection = codefConnection;
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            return connection != null && userId.equals(connection.getUserId())
                    && provider.equals(connection.getProvider()) ? connection : null;
        }

        @Override
        public List<CodefConnection> findByUserIdsAndProvider(List<Long> userIds, String provider) {
            return userIds.stream()
                    .map(userId -> findByUserIdAndProvider(userId, provider))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }
    }
}
