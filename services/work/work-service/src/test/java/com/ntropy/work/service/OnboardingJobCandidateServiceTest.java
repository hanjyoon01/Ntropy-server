package com.ntropy.work.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.ntropy.work.domain.JobCandidate;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.mapper.InMemoryCategoryMapper;
import com.ntropy.work.mapper.InMemoryPlatformMapper;
import com.ntropy.work.port.account.IncomingTransaction;
import com.ntropy.work.port.account.IncomingTransactionPort;

class OnboardingJobCandidateServiceTest {

    private static final long DELIVERY_CATEGORY_ID = 1L;
    private static final long DESIGNATED_DRIVING_CATEGORY_ID = 2L;

    private final InMemoryPlatformMapper platformMapper = new InMemoryPlatformMapper();
    private final InMemoryCategoryMapper categoryMapper = new InMemoryCategoryMapper();
    private StubIncomingTransactionPort incomingTransactionPort;
    private OnboardingJobCandidateService service;

    @BeforeEach
    void setUp() {
        categoryMapper.seed(Category.builder().categoryId(DELIVERY_CATEGORY_ID).name("배달").build());
        platformMapper.seed(platform(1L, DELIVERY_CATEGORY_ID, "배달의민족", "우아한형제들"));
        platformMapper.seed(platform(2L, DELIVERY_CATEGORY_ID, "쿠팡이츠", "쿠팡이츠"));
        platformMapper.seed(platform(4L, DESIGNATED_DRIVING_CATEGORY_ID, "카카오T대리", "카카오모빌리티"));
        incomingTransactionPort = new StubIncomingTransactionPort();
        service = new OnboardingJobCandidateService(incomingTransactionPort, platformMapper, categoryMapper);
    }

    @Test
    @DisplayName("배달이 아닌 단일 플랫폼은 platformName 후보로 만들어진다")
    void deriveJobCandidates_nonDeliverySinglePlatform_returnsSinglePlatformCandidate() {
        incomingTransactionPort.transactions = List.of(
                transaction("카카오모빌리티", 100_000L),
                transaction("카카오모빌리티", 120_000L)
        );

        List<JobCandidate> candidates = service.deriveJobCandidates(1L);

        assertEquals(1, candidates.size());
        JobCandidate candidate = candidates.get(0);
        assertEquals(1, candidate.platforms().size());
        assertEquals(2, candidate.settlementCount());
        assertEquals(BigDecimal.valueOf(220_000L), candidate.totalAmount());
    }

    @Test
    @DisplayName("배달 카테고리에서 여러 플랫폼이 매칭되면 하나의 후보로 묶인다")
    void deriveJobCandidates_multipleDeliveryPlatforms_merged() {
        incomingTransactionPort.transactions = List.of(
                transaction("우아한형제들", 150_000L),
                transaction("쿠팡이츠", 80_000L)
        );

        List<JobCandidate> candidates = service.deriveJobCandidates(1L);

        assertEquals(1, candidates.size());
        JobCandidate candidate = candidates.get(0);
        assertEquals("배달", candidate.categoryName());
        assertEquals(2, candidate.platforms().size());
        assertEquals(2, candidate.settlementCount());
        assertEquals(BigDecimal.valueOf(230_000L), candidate.totalAmount());
    }

    @Test
    @DisplayName("PLATFORM과 일치하지 않는 거래는 후보에서 제외된다")
    void deriveJobCandidates_excludesUnmatchedTransactions() {
        incomingTransactionPort.transactions = List.of(
                transaction("알수없는입금처", 50_000L)
        );

        List<JobCandidate> candidates = service.deriveJobCandidates(1L);

        assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("정규화 후 여러 PLATFORM과 동시에 일치하는 거래(복수일치)는 후보에서 제외된다")
    void deriveJobCandidates_excludesAmbiguousTransactions() {
        platformMapper.seed(platform(3L, DESIGNATED_DRIVING_CATEGORY_ID, "카카오T대리(중복)", "카카오 모빌리티"));
        incomingTransactionPort.transactions = List.of(
                transaction("카카오모빌리티", 100_000L)
        );

        List<JobCandidate> candidates = service.deriveJobCandidates(1L);

        assertTrue(candidates.isEmpty());
    }

    @Test
    @DisplayName("후보 목록은 총 입금액 기준 내림차순으로 반환된다")
    void deriveJobCandidates_sortedByTotalAmountDescending() {
        incomingTransactionPort.transactions = List.of(
                transaction("카카오모빌리티", 50_000L),
                transaction("우아한형제들", 300_000L)
        );

        List<JobCandidate> candidates = service.deriveJobCandidates(1L);

        assertEquals(BigDecimal.valueOf(300_000L), candidates.get(0).totalAmount());
        assertEquals(BigDecimal.valueOf(50_000L), candidates.get(1).totalAmount());
    }

    @Test
    @DisplayName("최근 3개월 lookback 기간으로 입금 내역을 조회한다")
    void deriveJobCandidates_queriesThreeMonthLookback() {
        incomingTransactionPort.transactions = List.of();

        service.deriveJobCandidates(1L);

        LocalDate expectedStart = LocalDate.now().minusMonths(3);
        assertEquals(expectedStart, incomingTransactionPort.lastStartDate);
        assertEquals(LocalDate.now(), incomingTransactionPort.lastEndDate);
    }

    private static Platform platform(long platformId, long categoryId, String platformName, String depositName) {
        return Platform.builder()
                .platformId(platformId)
                .categoryId(categoryId)
                .platformName(platformName)
                .depositName(depositName)
                .build();
    }

    private static IncomingTransaction transaction(String counterpartyName, long amount) {
        return new IncomingTransaction(
                1L, LocalDate.now(), LocalTime.NOON, counterpartyName, BigDecimal.valueOf(amount));
    }

    private static final class StubIncomingTransactionPort implements IncomingTransactionPort {
        private List<IncomingTransaction> transactions = List.of();
        private LocalDate lastStartDate;
        private LocalDate lastEndDate;

        @Override
        public List<IncomingTransaction> findIncomingTransactions(
                Long userId, LocalDate startDate, LocalDate endDate) {
            this.lastStartDate = startDate;
            this.lastEndDate = endDate;
            return transactions;
        }
    }
}
