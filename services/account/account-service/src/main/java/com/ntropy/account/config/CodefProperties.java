package com.ntropy.account.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

@Getter
@Component
public class CodefProperties {

    private final CodefServiceType serviceType;
    private final String clientId;
    private final String clientSecret;
    private final String publicKey;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public CodefProperties(
            @Value("${codef.service-type:SANDBOX}") String serviceType,
            @Value("${codef.client-id:${codef.sandbox.client-id:}}") String clientId,
            @Value("${codef.client-secret:${codef.sandbox.client-secret:}}") String clientSecret,
            @Value("${codef.public-key:}") String publicKey,
            @Value("${codef.connect-timeout-ms:5000}") int connectTimeoutMillis,
            @Value("${codef.read-timeout-ms:10000}") int readTimeoutMillis
    ) {
        this.serviceType = CodefServiceType.from(serviceType);
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.publicKey = publicKey;
        this.connectTimeoutMillis = connectTimeoutMillis;
        this.readTimeoutMillis = readTimeoutMillis;
    }
}
