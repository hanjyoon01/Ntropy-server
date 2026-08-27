package com.ntropy.work.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.Platform;

/**
 * 테스트용 인메모리 PlatformMapper 구현체. 여러 서비스 테스트에서 공용으로 사용한다.
 */
public class InMemoryPlatformMapper implements PlatformMapper {

    private final Map<Long, Platform> store = new LinkedHashMap<>();

    public void seed(Platform platform) {
        store.put(platform.getPlatformId(), platform);
    }

    @Override
    public void insert(Platform platform) {
        store.put(platform.getPlatformId(), platform);
    }

    @Override
    public Platform findById(Long platformId) {
        return store.get(platformId);
    }

    @Override
    public List<Platform> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(Platform platform) {
        store.put(platform.getPlatformId(), platform);
    }

    @Override
    public void deleteById(Long platformId) {
        store.remove(platformId);
    }
}
