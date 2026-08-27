package com.ntropy.account.client.codef;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 은행 계좌 상품 API 클라이언트.
 * 실제 응답 구조를 확인하는 PoC 단계이므로 data를 도메인 DTO로 고정하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CodefBankAccountClient {

    private static final String PERSONAL_ACCOUNT_LIST_PATH = "/v1/kr/bank/p/account/account-list";

    private final CodefApiClient codefApiClient;

    public JsonNode getPersonalAccountList(String organizationCode, String connectedId) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("organization", organizationCode);
        requestBody.put("connectedId", connectedId);

        JsonNode response = codefApiClient.post(PERSONAL_ACCOUNT_LIST_PATH, requestBody, JsonNode.class);
        String resultCode = response.path("result").path("code").asText();
        if (!"CF-00000".equals(resultCode)) {
            String message = response.path("result").path("message").asText("알 수 없는 오류");
            throw new IllegalStateException("CODEF 개인 보유계좌 조회 실패: " + resultCode + " " + message);
        }
        if (!response.path("data").isObject()) {
            throw new IllegalStateException("CODEF 개인 보유계좌 응답의 data가 객체가 아닙니다");
        }
        return response;
    }
}
