package com.ntropy.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.user.api.dto.VirtualUserDataset;
import com.ntropy.user.config.VirtualTestProperties;
import com.ntropy.user.domain.entity.User;
import com.ntropy.user.mapper.UserMapper;
import com.ntropy.user.virtual.VirtualUserProviderId;

@ExtendWith(MockitoExtension.class)
class VirtualUserSeedServiceTest {

    @Mock
    private UserMapper userMapper;
    @Mock
    private VirtualTestProperties properties;

    private VirtualUserSeedService service;

    @BeforeEach
    void setUp() {
        service = new VirtualUserSeedService(userMapper, properties);
    }

    @Test
    void disabledFeatureDoesNotExposePreviouslySeededDataset() {
        when(properties.isEnabled()).thenReturn(false);

        ServiceException exception = assertThrows(ServiceException.class, service::findSeededVirtualUsers);

        assertEquals(404, exception.getStatusCode());
        verifyNoInteractions(userMapper);
    }

    @Test
    void datasetContainsOnlyConfiguredOrdinalRange() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.isVirtualUserNumberInRange(1)).thenReturn(true);
        when(properties.isVirtualUserNumberInRange(2)).thenReturn(true);
        when(properties.isVirtualUserNumberInRange(3)).thenReturn(false);
        when(properties.getDatasetVersion()).thenReturn("dataset-v1");
        when(properties.getReferenceDate()).thenReturn(LocalDate.of(2026, 8, 17));
        when(properties.getRandomSeed()).thenReturn(123L);
        when(userMapper.findAllByProvider(VirtualUserProviderId.PROVIDER)).thenReturn(List.of(
                user(103L, 3), user(101L, 1), user(102L, 2)
        ));

        VirtualUserDataset dataset = service.findSeededVirtualUsers();

        assertEquals(List.of(1, 2), dataset.users().stream().map(user -> user.ordinal()).toList());
        assertEquals(List.of(101L, 102L), dataset.users().stream().map(user -> user.userId()).toList());
        assertEquals("dataset-v1", dataset.context().datasetVersion());
    }

    @Test
    void malformedProviderIdFailsInsteadOfEnteringAnotherDomainDataset() {
        when(properties.isEnabled()).thenReturn(true);
        User malformed = new User();
        malformed.setUserId(1L);
        malformed.setProviderId("unexpected-id");
        when(userMapper.findAllByProvider(VirtualUserProviderId.PROVIDER)).thenReturn(List.of(malformed));

        assertThrows(IllegalArgumentException.class, service::findSeededVirtualUsers);
    }

    private static User user(Long userId, int ordinal) {
        User user = new User();
        user.setUserId(userId);
        user.setProviderId(VirtualUserProviderId.forOrdinal(ordinal));
        return user;
    }
}
