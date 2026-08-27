package com.ntropy.account.domain;

/**
 * {@code CODEF_CONNECTION.provider} 구분값.
 * 사용자당 제공자별로 하나의 연결만 존재한다 (uk_codef_connection_user_provider).
 */
public enum ConnectionProvider {

    /** 실제 CODEF API로 발급받은 connectedId. */
    CODEF,

    /** 실제 CODEF API를 호출하지 않고 서버에서 발급한 가상 connectedId (이슈 #35). */
    NTROPY
}
