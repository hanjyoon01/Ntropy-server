package com.ntropy.account.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.stereotype.Component;

import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.TransactionFingerprint;
import com.ntropy.account.domain.entity.AccountTransaction;

/**
 * {@code provider=NTROPY} 연결의 일일 증분 거래를 생성한다 (이슈 #158).
 *
 * <p>{@link VirtualFinancialTransactionGenerator}는 계정 최초 생성 시 3개월치를 한 번에 만드는
 * 용도라 "현재 월 기준 상대 위치"를 시드로 쓴다. 그 방식을 호출 시점(referenceDate)이 매일 달라지는
 * 일일 증분 호출에 그대로 재사용하면, 같은 날짜의 거래가 호출한 날짜에 따라 다른 값으로 생성될 수
 * 있다. 이 클래스는 오직 (사용자, 거래일)만을 결정적 시드로 써서 언제 호출해도 같은 날짜는 항상
 * 같은 거래를 만든다 — 안전 중첩 구간을 다시 생성해도 fingerprint가 같아 중복 행이 생기지 않는다.
 *
 * <p>소비 거래와 환불·캐시백·예금이자 등 비잡 입금만 생성한다. work-service 정산과 연결되는
 * 플랫폼 잡 소득, 고정비 자동납부, 적금/대출 이체는 만들지 않는다(이번 이슈 범위 밖).
 * 잔액 연속성(afterBalance)은 매 호출마다 그 시점의 계좌 잔액을 기준으로 다시 계산해야 하는데,
 * 그 기준값 자체가 재실행마다 달라질 수 있어 fingerprint 안정성과 상충한다. 초기 3개월 데이터셋의
 * 잔액 정합성은 이미 보장돼 있으므로, 일일 증분 거래는 금액만 기록하고 afterBalance는 남기지 않는다.
 */
@Component
public class NtropyIncrementalTransactionGenerator {

    private static final MerchantSpend[] MERCHANT_SPENDS = {
            new MerchantSpend("쿠팡", 8_000L, 60_000L),
            new MerchantSpend("이마트", 5_000L, 45_000L),
            new MerchantSpend("스타벅스", 4_500L, 12_000L),
            new MerchantSpend("GS25", 2_000L, 15_000L),
            new MerchantSpend("배달의민족", 12_000L, 35_000L),
            new MerchantSpend("올리브영", 8_000L, 40_000L),
            new MerchantSpend("한솥도시락", 5_000L, 9_000L),
            new MerchantSpend("CGV", 12_000L, 30_000L)
    };
    private static final String[] REFUND_COUNTERPARTIES = {"쿠팡", "김밥천국", "올리브영"};
    private static final int REFUND_PROBABILITY_PERCENT = 15;

    /**
     * {@code (startDate, endDate]} 구간의 증분 거래를 생성한다(startDate는 포함하지 않음).
     * {@code startDate}가 {@code null}이면 {@code endDate} 하루치만 생성한다.
     */
    public List<AccountTransaction> generate(Long userId, Long accountId, LocalDate startDate, LocalDate endDate) {
        if (userId == null || accountId == null || endDate == null) {
            throw new IllegalArgumentException("userId, accountId, endDate가 필요합니다");
        }
        List<AccountTransaction> transactions = new ArrayList<>();
        LocalDate cursor = startDate == null ? endDate : startDate.plusDays(1);
        while (!cursor.isAfter(endDate)) {
            transactions.addAll(consumptionTransactions(userId, accountId, cursor));
            nonJobIncomeTransaction(userId, accountId, cursor).ifPresent(transactions::add);
            cursor = cursor.plusDays(1);
        }
        return transactions;
    }

    private List<AccountTransaction> consumptionTransactions(Long userId, Long accountId, LocalDate date) {
        int count = 1 + Math.floorMod(seed(userId, date, "count"), 3);
        List<AccountTransaction> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            MerchantSpend merchant = MERCHANT_SPENDS[
                    Math.floorMod(seed(userId, date, "merchant" + i), MERCHANT_SPENDS.length)
            ];
            long amount = deterministicAmount(userId, date, "amount" + i, merchant.minAmount(), merchant.maxAmount());
            LocalTime time = LocalTime.of(
                    10 + Math.floorMod(seed(userId, date, "hour" + i), 12),
                    Math.floorMod(seed(userId, date, "minute" + i), 60)
            );
            result.add(transaction(
                    accountId, date, time, BigDecimal.valueOf(amount), BigDecimal.ZERO,
                    "체크카드", merchant.name()
            ));
        }
        return result;
    }

    private Optional<AccountTransaction> nonJobIncomeTransaction(Long userId, Long accountId, LocalDate date) {
        if (date.equals(date.with(TemporalAdjusters.lastDayOfMonth()))) {
            long amount = deterministicAmount(userId, date, "interest", 500L, 5_000L);
            return Optional.of(transaction(
                    accountId, date, LocalTime.of(23, 50), BigDecimal.ZERO, BigDecimal.valueOf(amount),
                    "예금이자", "예금이자"
            ));
        }
        if (Math.floorMod(seed(userId, date, "refund-roll"), 100) < REFUND_PROBABILITY_PERCENT) {
            String counterparty = REFUND_COUNTERPARTIES[
                    Math.floorMod(seed(userId, date, "refund-name"), REFUND_COUNTERPARTIES.length)
            ];
            long amount = deterministicAmount(userId, date, "refund-amount", 1_000L, 50_000L);
            return Optional.of(transaction(
                    accountId, date, LocalTime.of(9, 30), BigDecimal.ZERO, BigDecimal.valueOf(amount),
                    "카드취소", counterparty
            ));
        }
        return Optional.empty();
    }

    private static long deterministicAmount(Long userId, LocalDate date, String salt, long minAmount, long maxAmount) {
        long raw = minAmount + Math.floorMod((long) seed(userId, date, salt), maxAmount - minAmount + 1);
        return (raw / 100L) * 100L;
    }

    private static AccountTransaction transaction(Long accountId, LocalDate date, LocalTime time,
                                                   BigDecimal outAmount, BigDecimal inAmount,
                                                   String method, String counterparty) {
        AccountTransaction transaction = new AccountTransaction();
        transaction.setAccountId(accountId);
        transaction.setTransactionCategory(AccountTransactionCategory.ORDINARY);
        transaction.setTranDate(date);
        transaction.setTranTime(time);
        transaction.setOutAmount(outAmount);
        transaction.setInAmount(inAmount);
        transaction.setDesc2(method);
        transaction.setDesc3(counterparty);
        transaction.setFingerprint(TransactionFingerprint.hash(
                accountId, "NTROPY_DAILY", date, time, outAmount, inAmount, method, counterparty
        ));
        return transaction;
    }

    private static int seed(Long userId, LocalDate date, String salt) {
        return Objects.hash(userId, date, salt);
    }

    private record MerchantSpend(String name, long minAmount, long maxAmount) {
    }
}
