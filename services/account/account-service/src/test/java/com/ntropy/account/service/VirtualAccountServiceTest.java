package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.exception.AccountErrorCode;
import com.ntropy.account.mapper.AccountMapper;
import com.ntropy.account.mapper.CodefConnectionMapper;
import com.ntropy.common.exception.ServiceException;

class VirtualAccountServiceTest {

    @Test
    void createsVirtualAccountWithMaskedNumberAndZeroBalance() {
        VirtualAccountService service = newService();

        Account account = service.createVirtualAccount(1L, "0004");

        assertNotNull(account.getId());
        assertEquals("0004", account.getOrganizationCode());
        assertEquals(AccountGroup.DEPOSIT_TRUST, account.getAccountGroup());
        assertTrue(account.getAccountNoMasked().startsWith("****"));
        assertEquals(0, account.getBalance().compareTo(java.math.BigDecimal.ZERO));
    }

    @Test
    void reusesExistingAccountForSameConnectionAndInstitution() {
        VirtualAccountService service = newService();

        Account first = service.createVirtualAccount(1L, "0004");
        Account second = service.createVirtualAccount(1L, "0004");

        assertEquals(first.getId(), second.getId());
        assertEquals(first.getAccountNoHash(), second.getAccountNoHash());
    }

    @Test
    void rejectsUnsupportedBankCode() {
        VirtualAccountService service = newService();

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.createVirtualAccount(1L, "9999")
        );
        assertEquals(AccountErrorCode.UNSUPPORTED_BANK.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void createsVirtualAccountWithoutBankCredentials() {
        VirtualAccountService service = newService();

        Account account = service.createVirtualAccount(1L, "0004");

        assertNotNull(account.getId());
    }

    @Test
    void listsOnlyAccountsForRequestedUser() {
        VirtualAccountService service = newService();
        service.createVirtualAccount(1L, "0004");
        service.createVirtualAccount(2L, "0088");

        List<Account> accounts = service.listAccounts(1L);

        assertEquals(1, accounts.size());
        assertEquals(1L, accounts.get(0).getUserId());
    }

    @Test
    void scopesListAndDetailQueriesToNtropyProvider() {
        InMemoryAccountMapper accountMapper = new InMemoryAccountMapper();
        VirtualConnectionService connectionService = new VirtualConnectionService(new InMemoryCodefConnectionMapper());
        VirtualAccountService service = new VirtualAccountService(connectionService, accountMapper);
        Account created = service.createVirtualAccount(1L, "0004");

        service.listAccounts(1L);
        service.getAccountDetail(1L, created.getId());

        assertEquals("NTROPY", accountMapper.lastListProvider);
        assertEquals("NTROPY", accountMapper.lastDetailProvider);
        assertEquals(1L, accountMapper.lastDetailUserId);
    }

    @Test
    void rejectsDetailAccessByNonOwner() {
        VirtualAccountService service = newService();
        Account created = service.createVirtualAccount(1L, "0004");

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.getAccountDetail(2L, created.getId())
        );
        assertEquals(AccountErrorCode.ACCOUNT_NOT_FOUND.getStatusCode(), exception.getStatusCode());
    }

    @Test
    void rejectsDetailAccessForMissingAccount() {
        VirtualAccountService service = newService();

        ServiceException exception = assertThrows(
                ServiceException.class,
                () -> service.getAccountDetail(1L, 999L)
        );
        assertEquals(AccountErrorCode.ACCOUNT_NOT_FOUND.getStatusCode(), exception.getStatusCode());
    }

    private static VirtualAccountService newService() {
        VirtualConnectionService virtualConnectionService = new VirtualConnectionService(new InMemoryCodefConnectionMapper());
        return new VirtualAccountService(virtualConnectionService, new InMemoryAccountMapper());
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

    private static class InMemoryAccountMapper implements AccountMapper {

        private final Map<Long, Account> store = new HashMap<>();
        private long nextId = 1;
        private String lastListProvider;
        private String lastDetailProvider;
        private Long lastDetailUserId;

        @Override
        public void upsert(Account account) {
            Account existing = findByConnectionIdAndAccountNoHash(
                    account.getCodefConnectionId(), account.getAccountNoHash()
            );
            if (existing != null) {
                account.setId(existing.getId());
            } else if (account.getId() == null) {
                account.setId(nextId++);
            }
            store.put(account.getId(), account);
        }

        @Override
        public void upsertAll(List<Account> accounts) {
            accounts.forEach(this::upsert);
        }

        @Override
        public void updateAccountDetails(Account account) {
        }

        @Override
        public Account findByConnectionIdAndAccountNoHash(Long codefConnectionId, String accountNoHash) {
            return store.values().stream()
                    .filter(a -> codefConnectionId.equals(a.getCodefConnectionId())
                            && accountNoHash.equals(a.getAccountNoHash()))
                    .findFirst().orElse(null);
        }

        @Override
        public List<Account> findByConnectionIdAndAccountNoHashes(Long codefConnectionId, List<String> accountNoHashes) {
            return accountNoHashes.stream()
                    .map(hash -> findByConnectionIdAndAccountNoHash(codefConnectionId, hash))
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public Account findByIdAndUserIdAndProvider(Long id, Long userId, String provider) {
            lastDetailProvider = provider;
            lastDetailUserId = userId;
            Account account = store.get(id);
            return account != null && userId.equals(account.getUserId()) ? account : null;
        }

        @Override
        public List<Account> findByUserIdAndProvider(Long userId, String provider) {
            lastListProvider = provider;
            List<Account> result = new ArrayList<>();
            for (Account account : store.values()) {
                if (userId.equals(account.getUserId())) {
                    result.add(account);
                }
            }
            return result;
        }

        @Override
        public boolean existsAnyByUserIdAndProvider(Long userId, String provider) {
            return store.values().stream().anyMatch(a -> userId.equals(a.getUserId()));
        }

        @Override
        public void deleteByUserIdAndProvider(Long userId, String provider) {
            store.values().removeIf(a -> userId.equals(a.getUserId()));
        }
    }
}
