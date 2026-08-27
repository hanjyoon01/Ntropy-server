package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

class VirtualConnectionServiceTest {

    @Test
    void issuesNtropyPrefixedConnectedIdOnFirstCall() {
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        VirtualConnectionService service = new VirtualConnectionService(mapper);

        CodefConnection connection = service.getOrCreateConnection(1L);

        assertNotNull(connection.getId());
        assertEquals("NTROPY", connection.getProvider());
        assertTrue(connection.getConnectedId().startsWith("NTROPY-"));
    }

    @Test
    void reusesExistingVirtualConnectionForSameUser() {
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        VirtualConnectionService service = new VirtualConnectionService(mapper);

        CodefConnection first = service.getOrCreateConnection(1L);
        CodefConnection second = service.getOrCreateConnection(1L);

        assertEquals(first.getConnectedId(), second.getConnectedId());
    }

    @Test
    void doesNotCollideWithExistingRealCodefConnectionForSameUser() {
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        CodefConnection realConnection = new CodefConnection();
        realConnection.setUserId(1L);
        realConnection.setProvider(ConnectionProvider.CODEF.name());
        realConnection.setConnectedId("real-connected-id");
        mapper.upsert(realConnection);
        VirtualConnectionService service = new VirtualConnectionService(mapper);

        CodefConnection virtualConnection = service.getOrCreateConnection(1L);

        assertTrue(virtualConnection.getConnectedId().startsWith("NTROPY-"));
        CodefConnection stillReal = mapper.findByUserIdAndProvider(1L, ConnectionProvider.CODEF.name());
        assertEquals("real-connected-id", stillReal.getConnectedId());
    }

    @Test
    void registersNewInstitutionKeyOnce() {
        InMemoryCodefConnectionMapper mapper = new InMemoryCodefConnectionMapper();
        VirtualConnectionService service = new VirtualConnectionService(mapper);
        CodefConnection connection = service.getOrCreateConnection(1L);

        CodefConnection afterFirst = service.registerInstitution(connection, "0004");
        CodefConnection afterSecond = service.registerInstitution(afterFirst, "0004");

        assertEquals("[\"0004\"]", afterSecond.getRegisteredInstitutionKeys());
    }

    private static class InMemoryCodefConnectionMapper implements CodefConnectionMapper {

        private final Map<String, CodefConnection> store = new HashMap<>();
        private long nextId = 1;

        @Override
        public void insert(CodefConnection codefConnection) {
            upsert(codefConnection);
        }

        @Override
        public void insertIfAbsent(CodefConnection codefConnection) {
            String key = key(codefConnection.getUserId(), codefConnection.getProvider());
            if (!store.containsKey(key)) {
                upsert(codefConnection);
            }
        }

        @Override
        public void upsert(CodefConnection codefConnection) {
            String key = key(codefConnection.getUserId(), codefConnection.getProvider());
            CodefConnection existing = store.get(key);
            if (existing == null) {
                codefConnection.setId(nextId++);
            } else {
                codefConnection.setId(existing.getId());
            }
            store.put(key, codefConnection);
        }

        @Override
        public CodefConnection findByUserIdAndProvider(Long userId, String provider) {
            return store.get(key(userId, provider));
        }

        @Override
        public List<CodefConnection> findByUserIdsAndProvider(List<Long> userIds, String provider) {
            return userIds.stream()
                    .map(userId -> findByUserIdAndProvider(userId, provider))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        private static String key(Long userId, String provider) {
            return userId + ":" + provider;
        }
    }
}
