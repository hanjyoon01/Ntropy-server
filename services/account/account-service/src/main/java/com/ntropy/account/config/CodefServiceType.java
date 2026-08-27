package com.ntropy.account.config;

import java.util.Locale;

/**
 * CODEF 호출 환경과 환경별 API 호스트.
 */
public enum CodefServiceType {

    SANDBOX("https://sandbox.codef.io"),
    DEMO("https://development.codef.io"),
    API("https://api.codef.io");

    private final String apiBaseUrl;

    CodefServiceType(String apiBaseUrl) {
        this.apiBaseUrl = apiBaseUrl;
    }

    public String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public static CodefServiceType from(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 CODEF 서비스 타입입니다: " + value + " (SANDBOX, DEMO, API 중 하나)", e
            );
        }
    }
}
