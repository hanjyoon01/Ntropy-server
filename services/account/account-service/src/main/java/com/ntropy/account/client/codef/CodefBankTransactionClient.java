package com.ntropy.account.client.codef;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/**
 * CODEF 은행 개인 수시입출 거래내역 조회({@code /v1/kr/bank/p/account/transaction-list}) API 클라이언트.
 * SC은행만 요구하는 {@code accountPassword}는 이번 이슈(#158)부터 지원한다 — 계좌 비밀번호 자체를
 * 저장하는 기능은 범위 밖이라 항상 {@code null}로 전달한다({@link #getPersonalTransactionList}의
 * {@code includeNullAccountPassword} 참고).
 * 공식 문서는 {@code data}가 "계좌별 결과 목록"(배열)이라고 설명하지만, 계좌 하나만 조회하는 실제 DEMO
 * 응답은 배열이 아니라 계좌 요약 객체 하나를 그대로 반환한다(신한 DEMO로 확인). 파싱은 두 형태 모두 허용한다.
 */
@Component
@RequiredArgsConstructor
public class CodefBankTransactionClient {

    private static final String PERSONAL_TRANSACTION_LIST_PATH = "/v1/kr/bank/p/account/transaction-list";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    // orderBy 0: 최신순(기본값), inquiryType 1: 계좌상세 포함 (우리 API에서는 항상 전송)
    private static final String ORDER_BY_LATEST_FIRST = "0";
    private static final String INQUIRY_TYPE_WITH_DETAIL = "1";

    private final CodefApiClient codefApiClient;

    public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                               LocalDate startDate, LocalDate endDate, String birthDate) {
        return getPersonalTransactionList(
                organizationCode, connectedId, account, startDate, endDate, birthDate, false
        );
    }

    /**
     * @param includeNullAccountPassword SC은행 전용. 계좌 비밀번호를 저장·전송하지 않으므로 항상
     *                                    {@code accountPassword: null}을 명시적으로 함께 보낸다.
     */
    public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                               LocalDate startDate, LocalDate endDate, String birthDate,
                                               boolean includeNullAccountPassword) {
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("organization", organizationCode);
        requestBody.put("connectedId", connectedId);
        requestBody.put("account", account);
        requestBody.put("startDate", startDate.format(YYYYMMDD));
        requestBody.put("endDate", endDate.format(YYYYMMDD));
        requestBody.put("orderBy", ORDER_BY_LATEST_FIRST);
        requestBody.put("inquiryType", INQUIRY_TYPE_WITH_DETAIL);
        if (birthDate != null) {
            requestBody.put("birthDate", birthDate);
        }
        if (includeNullAccountPassword) {
            requestBody.put("accountPassword", null);
        }

        JsonNode response = codefApiClient.post(PERSONAL_TRANSACTION_LIST_PATH, requestBody, JsonNode.class);
        String resultCode = response.path("result").path("code").asText();
        if (!"CF-00000".equals(resultCode)) {
            String message = response.path("result").path("message").asText("알 수 없는 오류");
            throw new IllegalStateException("CODEF 개인 수시입출 거래내역 조회 실패: " + resultCode + " " + message);
        }
        JsonNode data = response.path("data");
        if (!data.isObject() && !data.isArray()) {
            throw new IllegalStateException("CODEF 개인 수시입출 거래내역 응답의 data 형식을 알 수 없습니다");
        }
        return response;
    }
}
