package com.ntropy.ai.dto.fastapi;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI 소비 분류 API 요청 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionClassificationRequest {

    private List<TransactionForClassification> transactions;
}