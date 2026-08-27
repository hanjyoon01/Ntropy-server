package com.ntropy.user.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ntropy.user.config.VirtualTestProperties;

@ExtendWith(MockitoExtension.class)
class VirtualUserSeedInitializerTest {

    @Mock
    private VirtualUserSeedService virtualUserSeedService;
    @Mock
    private VirtualTestProperties properties;
    @InjectMocks
    private VirtualUserSeedInitializer initializer;

    @Test
    void seedsOnStartupWhenEnabled() {
        when(properties.isEnabled()).thenReturn(true);

        initializer.afterPropertiesSet();

        verify(virtualUserSeedService).seed();
    }

    @Test
    void doesNothingOnStartupWhenDisabled() {
        when(properties.isEnabled()).thenReturn(false);

        initializer.afterPropertiesSet();

        verify(virtualUserSeedService, never()).seed();
    }
}
