package com.ntropy.ai.client;

import org.springframework.stereotype.Component;

import com.ntropy.ai.service.AiReportEmailDeliveryService;
import com.ntropy.ai.api.client.AiReportEmailDeliveryClient;
import com.ntropy.ai.api.dto.AiReportEmailDeliverySummary;

import lombok.RequiredArgsConstructor;

/** BFF가 사용하는 수동 이메일 전달 계약의 로컬 구현. */
@Component
@RequiredArgsConstructor
public class LocalAiReportEmailDeliveryClient implements AiReportEmailDeliveryClient {

    private final AiReportEmailDeliveryService deliveryService;

    @Override
    public AiReportEmailDeliverySummary deliver(Long userId, String yearMonth) {
        return deliveryService.deliver(userId, yearMonth);
    }
}
