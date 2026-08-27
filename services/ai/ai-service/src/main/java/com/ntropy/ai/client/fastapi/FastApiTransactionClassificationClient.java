package com.ntropy.ai.client.fastapi;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import com.ntropy.ai.dto.fastapi.TransactionClassificationRequest;
import com.ntropy.ai.dto.fastapi.TransactionClassificationResponse;
import com.ntropy.ai.dto.fastapi.TransactionForClassification;
import com.ntropy.ai.config.FastApiProperties;

/**
 * FastAPI 소비 분류 API 호출 Client입니다.
 */
@Component
public class FastApiTransactionClassificationClient {

    private final RestTemplate restTemplate;

    private final String fastApiBaseUrl;

    @Autowired
    public FastApiTransactionClassificationClient(FastApiProperties properties) {
        this.fastApiBaseUrl = properties.getBaseUrl();
        SimpleClientHttpRequestFactory requestFactory =
                new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        this.restTemplate = new RestTemplate(requestFactory);
    }

    protected FastApiTransactionClassificationClient() {
        this.fastApiBaseUrl = null;
        this.restTemplate = new RestTemplate();
    }

    public TransactionClassificationResponse classifyTransactions(
            List<TransactionForClassification> transactions
    ) {
        String url = fastApiBaseUrl + "/api/v1/classify-transactions";

        TransactionClassificationRequest request =
                new TransactionClassificationRequest(transactions);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<TransactionClassificationRequest> httpEntity =
                new HttpEntity<>(request, headers);

        return restTemplate.postForObject(
                url,
                httpEntity,
                TransactionClassificationResponse.class
        );
    }
}
