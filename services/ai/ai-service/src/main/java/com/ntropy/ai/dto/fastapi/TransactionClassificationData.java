package com.ntropy.ai.dto.fastapi;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI 공통 응답의 data 영역입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionClassificationData {

    private List<TransactionClassificationResult> results;
}