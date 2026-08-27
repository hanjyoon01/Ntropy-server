package com.ntropy.work.adapter.account;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.VirtualSettlementDepositCommandClient;
import com.ntropy.account.api.dto.VirtualSettlementDepositCommand;
import com.ntropy.account.api.dto.VirtualSettlementDepositResult;
import com.ntropy.work.port.account.SettlementDepositOutcome;
import com.ntropy.work.port.account.SettlementDepositPort;
import com.ntropy.work.port.account.SettlementDepositRequest;

import lombok.RequiredArgsConstructor;

/** account-service가 발행한 VirtualSettlementDepositCommandClient를 work의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class AccountSettlementDepositAdapter implements SettlementDepositPort {

    private final VirtualSettlementDepositCommandClient depositCommandClient;

    @Override
    public SettlementDepositOutcome createOrAdjust(SettlementDepositRequest request) {
        VirtualSettlementDepositResult result = depositCommandClient.createOrAdjust(
                new VirtualSettlementDepositCommand(
                        request.userId(),
                        request.platformId(),
                        request.periodStart(),
                        request.periodEnd(),
                        request.depositDate(),
                        request.amount(),
                        request.depositName()
                ));
        return new SettlementDepositOutcome(result.available(), result.transactionCreated());
    }
}
