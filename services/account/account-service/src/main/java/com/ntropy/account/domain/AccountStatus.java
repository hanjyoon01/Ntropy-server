package com.ntropy.account.domain;

/** 계좌 노출 상태. 비활성 계좌와 거래는 기본 조회에서 제외한다. */
public enum AccountStatus {
    ACTIVE,
    INACTIVE
}
