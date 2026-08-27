package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.ntropy.account.client.codef.CodefConnectionClient;
import com.ntropy.account.config.BirthDateEncryptionProperties;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.account.security.BirthDateCipher;

class CodefConnectionServiceTest {

    static BirthDateCipher testBirthDateCipher() {
        return new BirthDateCipher(
                new BirthDateEncryptionProperties("EnZnHF6ULrhAjwHSJs5+2lizbiv7BHiB+5sZ4YIKEmc=", 1)
        );
    }

    @Test
    void registersAccountAndReadsSavedConnectionBack() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnectionService service = new CodefConnectionService(connectionClient, mapper, testBirthDateCipher());

        CodefConnection saved = service.registerAndSave(
                1L,
                "0004",
                "BK",
                "P",
                "sandbox-user",
                "sandbox-password",
                null
        );

        assertNotNull(saved);
        assertEquals(1L, saved.getUserId());
        assertEquals("connected-id", saved.getConnectedId());
        assertEquals(1, connectionClient.createCalls);
        assertEquals(0, connectionClient.addCalls);
    }

    @Test
    void addsAccountToExistingConnectedIdWithoutReplacingIt() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnection existing = new CodefConnection();
        existing.setUserId(1L);
        existing.setProvider("CODEF");
        existing.setConnectedId("existing-connected-id");
        mapper.upsert(existing);
        CodefConnectionService service = new CodefConnectionService(connectionClient, mapper, testBirthDateCipher());

        CodefConnection saved = service.registerAndSave(
                1L, "0088", "BK", "P", "login-id", "password", null
        );

        assertEquals("existing-connected-id", saved.getConnectedId());
        assertEquals("existing-connected-id", connectionClient.connectedId);
        assertEquals("0088", connectionClient.organizationCode);
        assertEquals(0, connectionClient.createCalls);
        assertEquals(1, connectionClient.addCalls);
        assertEquals("[\"0088\"]", saved.getRegisteredInstitutionKeys());
    }

    @Test
    void updatesConnectionWhenInstitutionAlreadyRegistered() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnection existing = new CodefConnection();
        existing.setUserId(1L);
        existing.setProvider("CODEF");
        existing.setConnectedId("existing-connected-id");
        existing.setRegisteredInstitutionKeys("[\"0088\"]");
        mapper.upsert(existing);
        CodefConnectionService service = new CodefConnectionService(connectionClient, mapper, testBirthDateCipher());

        CodefConnection saved = service.registerAndSave(
                1L, "0088", "BK", "P", "login-id", "password", null
        );

        assertEquals("existing-connected-id", saved.getConnectedId());
        assertEquals(0, connectionClient.createCalls);
        assertEquals(0, connectionClient.addCalls);
        assertEquals(1, connectionClient.updateCalls);
        assertEquals("existing-connected-id", connectionClient.connectedId);
        assertEquals("0088", connectionClient.organizationCode);
        assertEquals("[\"0088\"]", saved.getRegisteredInstitutionKeys());
    }

    @Test
    void accumulatesInstitutionKeysAcrossMultipleBanks() {
        StubCodefConnectionClient connectionClient = new StubCodefConnectionClient();
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnection existing = new CodefConnection();
        existing.setUserId(1L);
        existing.setProvider("CODEF");
        existing.setConnectedId("existing-connected-id");
        existing.setRegisteredInstitutionKeys("[\"0004\"]");
        mapper.upsert(existing);
        CodefConnectionService service = new CodefConnectionService(connectionClient, mapper, testBirthDateCipher());

        CodefConnection saved = service.registerAndSave(
                1L, "0088", "BK", "P", "login-id", "password", null
        );

        assertEquals(1, connectionClient.addCalls);
        assertEquals("[\"0004\",\"0088\"]", saved.getRegisteredInstitutionKeys());
    }

    private static class StubCodefConnectionClient extends CodefConnectionClient {

        private int createCalls;
        private int addCalls;
        private int updateCalls;
        private String connectedId;
        private String organizationCode;

        StubCodefConnectionClient() {
            super(null, null);
        }

        @Override
        public String createConnection(String organizationCode, String businessType, String clientType,
                                       String loginId, String rawPassword, String birthDate) {
            createCalls++;
            this.organizationCode = organizationCode;
            return "connected-id";
        }

        @Override
        public void addConnection(String connectedId, String organizationCode,
                                  String businessType, String clientType,
                                  String loginId, String rawPassword, String birthDate) {
            addCalls++;
            this.connectedId = connectedId;
            this.organizationCode = organizationCode;
        }

        @Override
        public void updateConnection(String connectedId, String organizationCode,
                                     String businessType, String clientType,
                                     String loginId, String rawPassword, String birthDate) {
            updateCalls++;
            this.connectedId = connectedId;
            this.organizationCode = organizationCode;
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
