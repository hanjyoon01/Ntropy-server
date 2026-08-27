package com.ntropy.payment.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Map;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentConfigSummary {

    private String storeId;
    private Map<String, String> channels;
    private String customerId;
}
