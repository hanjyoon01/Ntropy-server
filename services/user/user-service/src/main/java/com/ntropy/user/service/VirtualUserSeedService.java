package com.ntropy.user.service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.api.client.VirtualUserQueryClient;
import com.ntropy.user.api.dto.VirtualDatasetContext;
import com.ntropy.user.api.dto.VirtualUserDataset;
import com.ntropy.user.api.dto.VirtualUserIdentity;
import com.ntropy.user.config.VirtualTestProperties;
import com.ntropy.user.domain.entity.User;
import com.ntropy.user.exception.UserErrorCode;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.virtual.VirtualUserProviderId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * USERS에 실제 가상회원(provider=NTROPY_TEST)을 멱등하게 시딩하고, 시딩 결과를
 * {@link VirtualUserQueryClient} 계약으로 다른 서비스 모듈에 제공한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VirtualUserSeedService implements VirtualUserQueryClient {

    private final UserMapper userMapper;
    private final VirtualTestProperties properties;

    /**
     * virtual-test.user-count만큼 가상회원을 멱등 시딩한다. 이미 존재하는 순번은 건드리지 않는다.
     * 행마다 DB unique key로 이미 원자적·멱등이라 전체를 하나의 트랜잭션으로 묶을 필요가 없다.
     * (이 클래스는 {@link VirtualUserQueryClient} 인터페이스를 구현하므로, 여기에 {@code @Transactional}을
     * 걸면 Spring이 인터페이스만 구현하는 JDK 동적 프록시로 감싸 구체 클래스 주입이 깨진다.)
     */
    public void seed() {
        requireEnabled();
        for (int ordinal = 1; ordinal <= properties.getUserCount(); ordinal++) {
            seedOne(ordinal);
        }
        log.info("가상회원 시딩 완료: userCount={}", properties.getUserCount());
    }

    private void seedOne(int ordinal) {
        String providerId = VirtualUserProviderId.forOrdinal(ordinal);
        User user = new User();
        user.setProvider(VirtualUserProviderId.PROVIDER);
        user.setProviderId(providerId);
        user.setName("가상회원" + ordinal);
        user.setEmail(providerId + "@ntropy.test");
        user.setStatus("ACTIVE");
        user.setRole("ROLE_USER");
        user.setTermsAgreed(true);
        user.setOnboardingCompleted(true);

        // (provider, provider_id) unique key 경합 시 insertUser는 새 행을 만들지 않고
        // 기존 행의 user_id로 수렴한다 (ON DUPLICATE KEY UPDATE). 동시 시딩에도 안전하다.
        userMapper.insertUser(user);
    }

    @Override
    public VirtualUserDataset findSeededVirtualUsers() {
        requireEnabled();
        List<User> seeded = userMapper.findAllByProvider(VirtualUserProviderId.PROVIDER);
        List<VirtualUserIdentity> identities = seeded.stream()
                .map(user -> new VirtualUserIdentity(user.getUserId(), VirtualUserProviderId.parseOrdinal(user.getProviderId())))
                .filter(identity -> properties.isVirtualUserNumberInRange(identity.ordinal()))
                .sorted(Comparator.comparingInt(VirtualUserIdentity::ordinal))
                .toList();
        VirtualDatasetContext context = new VirtualDatasetContext(
                properties.getDatasetVersion(), properties.getReferenceDate(), properties.getRandomSeed()
        );
        return new VirtualUserDataset(context, identities);
    }

    /** 테스트 로그인이 순번으로 특정 가상회원을 찾을 때 쓴다. 시딩되지 않은 순번이면 빈 값을 반환하고 새로 만들지 않는다. */
    public Optional<User> findSeededUserByOrdinal(int ordinal) {
        return userMapper.findByProviderAndProviderId(VirtualUserProviderId.PROVIDER, VirtualUserProviderId.forOrdinal(ordinal));
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new ServiceException(UserErrorCode.VIRTUAL_TEST_DISABLED);
        }
    }
}
