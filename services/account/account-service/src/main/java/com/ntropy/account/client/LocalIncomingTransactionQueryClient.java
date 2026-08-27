package com.ntropy.account.client;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.ntropy.account.api.client.IncomingTransactionQueryClient;
import com.ntropy.account.api.dto.internal.NormalizedIncomingTransaction;
import com.ntropy.account.domain.IncomingCounterpartyNameExtractor;
import com.ntropy.account.mapper.IncomingTransactionQueryMapper;
import com.ntropy.account.mapper.projection.IncomingTransactionRow;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalIncomingTransactionQueryClient implements IncomingTransactionQueryClient {

    private final IncomingTransactionQueryMapper incomingTransactionQueryMapper;

    @Override
    public List<NormalizedIncomingTransaction> findIncomingTransactions(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Objects.requireNonNull(userId, "userId는 필수입니다");
        Objects.requireNonNull(startDate, "startDate는 필수입니다");
        Objects.requireNonNull(endDate, "endDate는 필수입니다");
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate는 endDate 이후일 수 없습니다");
        }

        return incomingTransactionQueryMapper.findByUserIdAndDateRange(userId, startDate, endDate)
                .stream()
                .map(LocalIncomingTransactionQueryClient::toNormalizedTransaction)
                .toList();
    }

    private static NormalizedIncomingTransaction toNormalizedTransaction(IncomingTransactionRow row) {
        String counterpartyName = IncomingCounterpartyNameExtractor.extract(
                row.getOrganizationCode(),
                row.getDesc1(),
                row.getDesc2(),
                row.getDesc3(),
                row.getDesc4()
        );
        return new NormalizedIncomingTransaction(
                row.getTransactionId(),
                row.getTransactionDate(),
                row.getTransactionTime(),
                counterpartyName,
                row.getAmount()
        );
    }
}
