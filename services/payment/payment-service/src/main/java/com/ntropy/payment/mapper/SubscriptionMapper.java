package com.ntropy.payment.mapper;

import com.ntropy.payment.domain.Subscription;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SubscriptionMapper {

    Subscription findById(Long subscriptionId);

    Subscription findLatestByUserId(Long userId);

    Subscription findByCustomerUid(String customerUid);

    List<Subscription> findAllByUserId(Long userId);

    int insert(Subscription subscription);

    int update(Subscription subscription);
}