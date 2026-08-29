package com.ntropy.bff.dto.subscription.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class PaymentMethodUpdateRequest {
    private String billingKey;
}