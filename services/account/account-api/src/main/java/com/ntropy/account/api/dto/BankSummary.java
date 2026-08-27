package com.ntropy.account.api.dto;

/** 계좌 연결 화면에 노출할 지원 은행 정보. */
public record BankSummary(
        String organizationCode,
        String bankName,
        boolean birthDateRequired,
        boolean virtualSupported,
        boolean codefSupported,
        boolean transactionHistorySupported
) {
}
