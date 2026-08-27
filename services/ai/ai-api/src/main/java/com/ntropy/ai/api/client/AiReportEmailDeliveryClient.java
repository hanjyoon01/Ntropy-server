package com.ntropy.ai.api.client;

import com.ntropy.ai.api.dto.AiReportEmailDeliverySummary;

/** 인증 사용자의 AI 리포트를 가입 이메일로 전달하는 명령 계약. */
public interface AiReportEmailDeliveryClient {

    AiReportEmailDeliverySummary deliver(Long userId, String yearMonth);
}
