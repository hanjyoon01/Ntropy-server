package com.ntropy.account.client.codef.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code POST /v1/account/create} 응답 바디. CODEF 모든 API 공통 result/data 포맷을 따른다.
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class CodefConnectionCreateResponse {

    private Result result;
    private Data data;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private String code;
        private String message;
        private String extraMessage;
        private String transactionId;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private String connectedId;
        private List<AccountResult> successList;
        private List<AccountResult> errorList;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AccountResult {
        private String code;
        private String message;
        private String extraMessage;
        private String countryCode;
        private String businessType;
        private String clientType;
        private String loginType;
        private String organization;
    }
}
