package com.ntropy.user.service;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import com.ntropy.user.config.VirtualTestProperties;

import lombok.RequiredArgsConstructor;

/** local/test 환경에서 가상회원 기능을 활성화하면 애플리케이션 기동 중 멱등 시딩을 수행한다. */
@Component
@RequiredArgsConstructor
public class VirtualUserSeedInitializer implements InitializingBean {

    private final VirtualUserSeedService virtualUserSeedService;
    private final VirtualTestProperties properties;

    @Override
    public void afterPropertiesSet() {
        if (properties.isEnabled()) {
            virtualUserSeedService.seed();
        }
    }
}
