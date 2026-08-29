package com.ntropy.bff.dto.subscription.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentHistoryResponse {
    private List<PaymentHistoryItemResponse> payments;
}