package com.ntropy.account.repository;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import com.ntropy.account.config.CodefServiceType;
import com.ntropy.account.domain.entity.CodefToken;
import com.ntropy.account.mapper.CodefTokenMapper;

import lombok.RequiredArgsConstructor;

@Repository
@RequiredArgsConstructor
public class CodefTokenDbStore implements CodefTokenStore {

    private final CodefTokenMapper codefTokenMapper;

    @Override
    public void save(CodefToken token) {
        codefTokenMapper.insert(token);
    }

    @Override
    public Optional<CodefToken> findLatest(CodefServiceType serviceType, String clientId) {
        return Optional.ofNullable(codefTokenMapper.findLatest(serviceType.name(), clientId));
    }
}
