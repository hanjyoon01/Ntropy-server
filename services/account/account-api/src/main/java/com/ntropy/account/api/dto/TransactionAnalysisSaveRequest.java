package com.ntropy.account.api.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 거래 분석 결과 저장 요청 DTO입니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionAnalysisSaveRequest {

    private Long userId;
    private String yearMonth;
    private List<TransactionAnalysisSaveItem> analyses;
}
