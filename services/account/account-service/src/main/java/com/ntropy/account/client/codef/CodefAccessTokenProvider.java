package com.ntropy.account.client.codef;

/**
 * CODEF API 호출부가 토큰 저장 방식에 의존하지 않도록 분리한 토큰 공급자.
 */
public interface CodefAccessTokenProvider {

    String getValidAccessToken();

    String refreshAccessToken();
}
