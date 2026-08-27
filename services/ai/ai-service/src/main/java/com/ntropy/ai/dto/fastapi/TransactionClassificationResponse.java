package com.ntropy.ai.dto.fastapi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI 소비 분류 API 응답 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionClassificationResponse {

    private Boolean success;
    private Integer status_code;
    private String message;
    private TransactionClassificationData data;
}