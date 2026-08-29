package com.ntropy.account.event;

/** 계좌 연동으로 거래 저장을 마친 뒤 소비 분류를 요청하는 내부 이벤트입니다. */
public record AccountTransactionsCollectedEvent(Long userId) {
}
