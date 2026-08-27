package com.ntropy.account.repository;

import java.util.Optional;

import com.ntropy.account.config.CodefServiceType;
import com.ntropy.account.domain.entity.CodefToken;

/**
 * CODEF OAuth2 accessToken 저장/조회 인터페이스.
 * 지금은 DB 구현체({@link CodefTokenDbStore})만 있고, 추후 Redis 구현체로 교체할 예정이라
 * 호출부는 이 인터페이스만 보고 구현체 교체에 영향받지 않도록 분리한다.
 */
public interface CodefTokenStore {

    void save(CodefToken token);

    Optional<CodefToken> findLatest(CodefServiceType serviceType, String clientId);
}
