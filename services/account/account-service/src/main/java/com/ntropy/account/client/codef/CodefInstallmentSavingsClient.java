package com.ntropy.account.client.codef;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

/** CODEF 은행 개인 적금 거래내역 API 클라이언트. */
@Component
@RequiredArgsConstructor
public class CodefInstallmentSavingsClient {

    private static final String PATH = "/v1/kr/bank/p/installment-savings/transaction-list";
    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final String ORDER_BY_LATEST_FIRST = "0";
    private static final String INQUIRY_TYPE_WITH_DETAIL = "1";

    private final CodefApiClient codefApiClient;

    public JsonNode getPersonalTransactionList(String organizationCode, String connectedId, String account,
                                               LocalDate startDate, LocalDate endDate, String birthDate) {
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

        JsonNode response = codefApiClient.post(PATH, requestBody, JsonNode.class);
        String resultCode = response.path("result").path("code").asText();
        if (!"CF-00000".equals(resultCode)) {
            throw new IllegalStateException(
                    "CODEF 개인 적금 거래내역 조회 실패: " + CodefErrorMessage.from(response)
            );
        }
        JsonNode data = response.path("data");
        if (!data.isObject() && !data.isArray()) {
            throw new IllegalStateException("CODEF 개인 적금 거래내역 응답의 data 형식을 알 수 없습니다");
        }
        return response;
    }
}
