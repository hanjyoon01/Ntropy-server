package com.ntropy.ai.dto.fastapi;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * FastAPI 소비 분류 API에 전달할 거래 한 건의 요청 DTO입니다.
 *
 * AI #33에서 확정한 구조화된 거래 설명 필드 계약을 사용합니다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionForClassification {

    /**
     * ACCOUNT_TRANSACTION.account_transaction_id
     */
    private Long transactionId;

    /**
     * 소비 분류 대상 금액입니다.
     *
     * ORDINARY는 outAmount를 사용합니다.
     * INSTALLMENT와 LOAN은 Spring에서 결정적으로 처리하므로
     * FastAPI에는 일반적으로 전달하지 않습니다.
     */
    private Long amount;

    /**
     * ACCOUNT_TRANSACTION.transaction_category
     */
    private String transactionCategory;

    /**
     * CODEF 금융기관 코드입니다.
     */
    private String organizationCode;

    /**
     * CODEF resAccountDesc1 원문입니다.
     */
    private String desc1;

    /**
     * CODEF resAccountDesc2 원문입니다.
     */
    private String desc2;

    /**
     * CODEF resAccountDesc3 원문입니다.
     *
     * 주로 상대방, 가맹점 또는 금융상품명이 저장됩니다.
     */
    private String desc3;

    /**
     * CODEF resAccountDesc4 원문입니다.
     */
    private String desc4;
}