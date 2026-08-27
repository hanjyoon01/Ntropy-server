package com.ntropy.account.client.codef.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

/**
 * {@code POST https://oauth.codef.io/oauth/token} 응답 바디.
 */
@Getter
@Setter
public class CodefTokenResponse {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("expires_in")
    private Long expiresIn;

    private String scope;
}
