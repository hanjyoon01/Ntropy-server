package com.ntropy.payment.mapper;

import com.ntropy.payment.domain.Payment;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface PaymentMapper {

    Payment findById(Long paymentId);

    Payment findByMerchantUid(String merchantUid);

    List<Payment> findAllBySubscriptionId(Long subscriptionId);

    int insert(Payment payment);

    int update(Payment payment);

    int cancelPendingBySubscriptionId(Long subscriptionId);
}
