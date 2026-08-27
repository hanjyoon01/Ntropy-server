package com.ntropy.account.api.domain;

/**
 * 일일 금융거래 증분 동기화 대상 provider (이슈 #158).
 * account-service의 {@code ConnectionProvider}(com.ntropy.account.domain)와 값 집합은 같지만,
 * account-api가 account-service 패키지를 참조하면 순환 의존이 발생하므로 별도로 둔다.
 * account-service 구현체 내부에서 서로 매핑한다.
 */
public enum DailyFinancialSyncProvider {
    CODEF,
    NTROPY
}
