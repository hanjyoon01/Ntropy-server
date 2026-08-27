package com.ntropy.notification.mapper;

import java.util.List;
import java.util.Optional;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.notification.domain.entity.PushSubscription;

@Mapper
public interface PushSubscriptionMapper {

    void insert(PushSubscription subscription);

    List<PushSubscription> findByUserId(@Param("userId") Long userId);

    Optional<PushSubscription> findByEndpoint(@Param("endpoint") String endpoint);

    void deleteByEndpoint(@Param("endpoint") String endpoint);
}
