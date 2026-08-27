package com.ntropy.work.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.ntropy.work.domain.JobCandidate;
import com.ntropy.work.domain.PlatformMatch;
import com.ntropy.work.domain.PlatformMatchResult;
import com.ntropy.work.domain.PlatformMatcher;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.mapper.CategoryMapper;
import com.ntropy.work.mapper.PlatformMapper;
import com.ntropy.work.port.account.IncomingTransaction;
import com.ntropy.work.port.account.IncomingTransactionPort;

import lombok.RequiredArgsConstructor;

/**
 * 온보딩(잡 등록) 단계에서 최근 입금 내역을 PLATFORM과 매칭해 잡 등록 후보를 산출한다.
 * 배달(work-service-seed.sql 기준 category_id=1)은 여러 플랫폼을 동시에 운영하는 경우가
 * 많아 하나의 후보로 묶는다. 미일치·복수일치(같은 이름으로 여러 PLATFORM에 매칭) 거래는
 * 후보 산출에서 제외한다 (처리 방식 미확정).
 */
@Service
@RequiredArgsConstructor
public class OnboardingJobCandidateService {

    public static final int ONBOARDING_LOOKBACK_MONTHS = 3;

    /** work-service-seed.sql 기준 '배달' 카테고리 ID. */
    private static final Long DELIVERY_CATEGORY_ID = 1L;

    private final IncomingTransactionPort incomingTransactionPort;
    private final PlatformMapper platformMapper;
    private final CategoryMapper categoryMapper;

    public List<JobCandidate> deriveJobCandidates(Long userId) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(ONBOARDING_LOOKBACK_MONTHS);

        List<IncomingTransaction> transactions =
                incomingTransactionPort.findIncomingTransactions(userId, startDate, endDate);
        List<Platform> platforms = platformMapper.findAll();

        Map<Long, Aggregate> aggregatesByPlatform = new LinkedHashMap<>();
        for (IncomingTransaction transaction : transactions) {
            PlatformMatchResult result = PlatformMatcher.match(transaction.counterpartyName(), platforms);
            if (!(result instanceof PlatformMatchResult.Matched matched)) {
                continue;
            }
            Platform platform = matched.platform();
            Aggregate aggregate = aggregatesByPlatform.computeIfAbsent(
                    platform.getPlatformId(), id -> new Aggregate(platform));
            aggregate.count++;
            aggregate.totalAmount = aggregate.totalAmount.add(transaction.amount());
        }

        List<Aggregate> deliveryAggregates = new ArrayList<>();
        List<JobCandidate> candidates = new ArrayList<>();
        for (Aggregate aggregate : aggregatesByPlatform.values()) {
            if (aggregate.platform.getCategoryId().equals(DELIVERY_CATEGORY_ID)) {
                deliveryAggregates.add(aggregate);
            } else {
                candidates.add(toSinglePlatformCandidate(aggregate));
            }
        }
        if (!deliveryAggregates.isEmpty()) {
            candidates.add(toDeliveryCandidate(deliveryAggregates));
        }

        return candidates.stream()
                .sorted(Comparator.comparing(JobCandidate::totalAmount).reversed())
                .toList();
    }

    private JobCandidate toSinglePlatformCandidate(Aggregate aggregate) {
        Platform platform = aggregate.platform;
        return new JobCandidate(
                platform.getCategoryId(),
                null,
                List.of(toPlatformMatch(platform)),
                aggregate.count,
                aggregate.totalAmount
        );
    }

    private JobCandidate toDeliveryCandidate(List<Aggregate> deliveryAggregates) {
        Category category = categoryMapper.findById(DELIVERY_CATEGORY_ID);
        List<PlatformMatch> platformMatches = deliveryAggregates.stream()
                .map(aggregate -> toPlatformMatch(aggregate.platform))
                .toList();
        int totalCount = deliveryAggregates.stream().mapToInt(aggregate -> aggregate.count).sum();
        BigDecimal totalAmount = deliveryAggregates.stream()
                .map(aggregate -> aggregate.totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new JobCandidate(
                DELIVERY_CATEGORY_ID,
                category.getName(),
                platformMatches,
                totalCount,
                totalAmount
        );
    }

    private PlatformMatch toPlatformMatch(Platform platform) {
        return new PlatformMatch(platform.getPlatformId(), platform.getPlatformName(), platform.getDepositName());
    }

    private static final class Aggregate {
        private final Platform platform;
        private int count;
        private BigDecimal totalAmount = BigDecimal.ZERO;

        private Aggregate(Platform platform) {
            this.platform = platform;
        }
    }
}
