package com.ntropy.account.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.ntropy.account.domain.AccountBalanceConsistencyValidator;
import com.ntropy.account.domain.AccountGroup;
import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;
import com.ntropy.account.service.VirtualFinancialTransactionGenerator.GeneratedTransactions;

class VirtualFinancialTransactionGeneratorTest {

    private static final Set<String> INSURANCE_PRODUCTS = Set.of(
            "삼성생명 실손보험", "KB 이륜차 운전자보험"
    );
    private static final Set<String> INCOME_COUNTERPARTY_NAMES = Set.of(
            "우아한청년들", "쿠팡이츠정산", "위대한상상", "카카오모빌리티", "펫피플"
    );
    // work-service PLATFORM.platform_name 시드값 중 deposit_name과 다른 것들.
    // (로젠택배·케어닥은 platform_name==deposit_name이라 제외, 미소는 platform_name "미소"가
    // deposit_name "유한회사미소"의 부분 문자열이라 contains 오탐이 생겨 제외 — 정산 수입에 정상적으로 등장한다.)
    // 거래 상대방명에는 플랫폼 표시명 대신 실제 정산처명이 저장돼야 한다.
    private static final Set<String> PLATFORM_DISPLAY_NAME_TOKENS = Set.of(
            "배민커넥트", "쿠팡이츠 배달파트너", "요기요라이더", "카카오T대리", "유튜브",
            "쿠팡플렉스", "티맵 대리", "CJ대한통운 상하차", "청소연구소(청연)", "펫플래닛", "틱톡",
            "네이버TV"
    );
    private static final Set<String> MEDIUM_PREFIXES = Set.of(
            "KG이니시스_", "토스페이_", "네이버페이_", "카카오페이_", "나이스페이_", "NHN페이코_"
    );
    private static final Set<String> HARD_NAMES = Set.of(
            "주식회사 더원", "에이치앤에스", "케이에스컴퍼니", "스마트로",
            "제이앤파트너스", "한올유통", "비앤에스코퍼레이션", "지오정보통신"
    );
    private static final Set<String> UNDECIDABLE_NAMES = Set.of("김민수", "결제", "기타", "1234PAY");

    // 월말을 기준일로 고정해 "현재 월"이 항상 완결된 3개월 창(2026-04~06)이 되도록 하고, 기존 고정 기간 검증값을 그대로 유지한다.
    private static final LocalDate REFERENCE_DATE = LocalDate.of(2026, 6, 30);

    private final VirtualFinancialTransactionGenerator generator = new VirtualFinancialTransactionGenerator();

    @Test
    void generatesTransactionsPerMonthWithAllFinancialPatterns() {
        Account ordinary = account(10L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(11L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(12L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                1, PersonalBank.SHINHAN_BANK, ordinary, installment, loan
        );

        assertEquals(306, generated.transactions().size());
        Map<YearMonth, Long> monthlyCounts = generated.transactions().stream()
                .collect(Collectors.groupingBy(
                        transaction -> YearMonth.from(transaction.getTranDate()),
                        Collectors.counting()
                ));
        assertEquals(102L, monthlyCounts.get(YearMonth.of(2026, 4)));
        assertEquals(102L, monthlyCounts.get(YearMonth.of(2026, 5)));
        assertEquals(102L, monthlyCounts.get(YearMonth.of(2026, 6)));

        assertEquals(300, countCategory(generated.transactions(), AccountTransactionCategory.ORDINARY));
        assertEquals(3, countCategory(generated.transactions(), AccountTransactionCategory.INSTALLMENT));
        assertEquals(3, countCategory(generated.transactions(), AccountTransactionCategory.LOAN));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.INSTALLMENT)
                .allMatch(transaction -> transaction.getTranDate().getDayOfMonth() == 25));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .allMatch(transaction -> transaction.getTranDate().getDayOfMonth() == 25));
        assertTrue(generated.transactions().stream().anyMatch(transaction -> transaction.getInAmount().signum() > 0));
        assertTrue(generated.transactions().stream().anyMatch(transaction -> transaction.getOutAmount().signum() > 0));
        List<AccountTransaction> incomeTransactions = generated.transactions().stream()
                .filter(transaction -> transaction.getInAmount().signum() > 0)
                .filter(transaction -> INCOME_COUNTERPARTY_NAMES.contains(transaction.getDesc3()))
                .toList();
        assertEquals(30, incomeTransactions.size());
        assertEquals(Set.of("우아한청년들", "카카오모빌리티"), incomeTransactions.stream()
                .map(AccountTransaction::getDesc3).collect(Collectors.toSet()));
    }

    @Test
    void assignsThreeIncomeCounterpartiesToEverySecondUserAndGeneratesLoanRepayments() {
        Account ordinary = account(20L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(22L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(21L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                REFERENCE_DATE, 26, PersonalBank.NH_BANK, ordinary, installment, loan
        );

        assertEquals(3, generated.userIncomeCounterpartyCount());
        assertEquals(3, generated.transactions().stream()
                .filter(transaction -> transaction.getInAmount().signum() > 0)
                .map(AccountTransaction::getDesc3).filter(INCOME_COUNTERPARTY_NAMES::contains).distinct().count());
        assertEquals(3, countCategory(generated.transactions(), AccountTransactionCategory.LOAN));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .allMatch(transaction -> transaction.getOutAmount().signum() > 0));
        assertTrue(generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .allMatch(transaction -> transaction.getTranDate().getDayOfMonth() == 25));
    }

    @Test
    void generatesStableUniqueFingerprintsAndConsistentBalances() {
        Account ordinary = account(30L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(31L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(32L, AccountGroup.LOAN);

        GeneratedTransactions first = generator.generate(
                REFERENCE_DATE, 3, PersonalBank.KB_KOOKMIN_BANK, ordinary, installment, loan
        );
        GeneratedTransactions second = generator.generate(
                REFERENCE_DATE, 3, PersonalBank.KB_KOOKMIN_BANK, ordinary, installment, loan
        );

        List<String> firstFingerprints = first.transactions().stream()
                .map(AccountTransaction::getFingerprint).toList();
        List<String> secondFingerprints = second.transactions().stream()
                .map(AccountTransaction::getFingerprint).toList();
        assertEquals(firstFingerprints, secondFingerprints);
        assertEquals(306, Set.copyOf(firstFingerprints).size());

        verifyAccountBalance(ordinary, first);
        verifyAccountBalance(installment, first);
        verifyAccountBalance(loan, first);
    }

    @Test
    void followsBankSpecificOptionalDescriptionFields() {
        Account ordinary = account(40L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(41L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(42L, AccountGroup.LOAN);

        GeneratedTransactions jeonbuk = generator.generate(REFERENCE_DATE,
                5, PersonalBank.JEONBUK_BANK, ordinary, installment, loan
        );
        assertTrue(jeonbuk.transactions().stream().allMatch(transaction -> transaction.getDesc2() == null));

        GeneratedTransactions ibk = generator.generate(REFERENCE_DATE,
                5, PersonalBank.IBK_INDUSTRIAL_BANK, ordinary, installment, loan
        );
        assertTrue(ibk.transactions().stream().allMatch(transaction -> transaction.getDesc4() == null));
        assertTrue(ibk.transactions().stream()
                .filter(transaction -> transaction.getInAmount().signum() > 0)
                .filter(transaction -> INCOME_COUNTERPARTY_NAMES.contains(transaction.getDesc3()))
                .allMatch(transaction -> transaction.getDesc1() != null));
        assertNull(ibk.transactions().get(0).getDesc4());
    }

    @Test
    void appliesConsumerProfileToMerchantMixAndSpendingLevel() {
        GeneratedTransactions rational = generator.generate(REFERENCE_DATE,
                1, PersonalBank.SHINHAN_BANK,
                account(50L, AccountGroup.DEPOSIT_TRUST), account(51L, AccountGroup.DEPOSIT_TRUST),
                account(59L, AccountGroup.LOAN)
        );
        GeneratedTransactions trend = generator.generate(REFERENCE_DATE,
                2, PersonalBank.SHINHAN_BANK,
                account(52L, AccountGroup.DEPOSIT_TRUST), account(53L, AccountGroup.DEPOSIT_TRUST),
                account(60L, AccountGroup.LOAN)
        );
        GeneratedTransactions value = generator.generate(REFERENCE_DATE,
                3, PersonalBank.SHINHAN_BANK,
                account(54L, AccountGroup.DEPOSIT_TRUST), account(55L, AccountGroup.DEPOSIT_TRUST),
                account(61L, AccountGroup.LOAN)
        );
        GeneratedTransactions impulse = generator.generate(REFERENCE_DATE,
                4, PersonalBank.SHINHAN_BANK,
                account(56L, AccountGroup.DEPOSIT_TRUST), account(57L, AccountGroup.DEPOSIT_TRUST),
                account(62L, AccountGroup.LOAN)
        );

        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.RATIONAL_FRUGAL,
                VirtualFinancialTransactionGenerator.consumerProfileFor(1));
        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.TREND_STATUS,
                VirtualFinancialTransactionGenerator.consumerProfileFor(2));
        assertTrue(hasMerchant(rational, "다이소"));
        assertTrue(hasMerchant(trend, "신세계백화점"));
        assertTrue(hasMerchant(value, "제로웨이스트샵"));
        assertTrue(hasMerchant(impulse, "구글플레이"));
        assertTrue(averageCardSpending(trend) > averageCardSpending(rational));
    }

    @Test
    void appliesDatasetRandomSeedToConsumerProfile() {
        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.RATIONAL_FRUGAL,
                VirtualFinancialTransactionGenerator.consumerProfileFor(1, 0L));
        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.TREND_STATUS,
                VirtualFinancialTransactionGenerator.consumerProfileFor(1, 1L));
        assertEquals(VirtualFinancialTransactionGenerator.ConsumerProfile.IMPULSE_INDIFFERENT,
                VirtualFinancialTransactionGenerator.consumerProfileFor(1, -1L));

        GeneratedTransactions seeded = generator.generate(
                REFERENCE_DATE, 1, 1L, PersonalBank.SHINHAN_BANK,
                account(58L, AccountGroup.DEPOSIT_TRUST), account(63L, AccountGroup.DEPOSIT_TRUST),
                account(64L, AccountGroup.LOAN)
        );
        assertTrue(hasMerchant(seeded, "신세계백화점"));
    }

    @Test
    void assignsLoggedInUserProfileByStableUserIdHash() {
        for (long userId = 1; userId <= 20; userId++) {
            int expectedIndex = Math.floorMod(Long.hashCode(userId), 4);
            assertEquals(
                    VirtualFinancialTransactionGenerator.ConsumerProfile.values()[expectedIndex],
                    VirtualFinancialTransactionGenerator.consumerProfileForUser(userId)
            );
        }
    }

    @Test
    void assignsOneOrTwoInsuranceProductsAndPreservesBankDescriptions() {
        GeneratedTransactions oddUser = generator.generate(REFERENCE_DATE,
                1, PersonalBank.IBK_INDUSTRIAL_BANK,
                account(60L, AccountGroup.DEPOSIT_TRUST), account(61L, AccountGroup.DEPOSIT_TRUST),
                account(65L, AccountGroup.LOAN)
        );
        GeneratedTransactions evenUser = generator.generate(REFERENCE_DATE,
                2, PersonalBank.NH_BANK,
                account(62L, AccountGroup.DEPOSIT_TRUST), account(66L, AccountGroup.DEPOSIT_TRUST),
                account(67L, AccountGroup.LOAN)
        );

        List<AccountTransaction> oddInsurance = insuranceTransactions(oddUser);
        List<AccountTransaction> evenInsurance = insuranceTransactions(evenUser);
        assertEquals(3, oddInsurance.size());
        assertEquals(Set.of("삼성생명 실손보험"), oddInsurance.stream()
                .map(AccountTransaction::getDesc3).collect(Collectors.toSet()));
        assertTrue(oddInsurance.stream().allMatch(transaction -> "삼성생명".equals(transaction.getDesc1())));

        assertEquals(6, evenInsurance.size());
        assertEquals(2, evenInsurance.stream().map(AccountTransaction::getDesc3).distinct().count());
        assertTrue(evenInsurance.stream().allMatch(transaction -> "보험료".equals(transaction.getDesc2())));
    }

    @Test
    void doesNotGenerateFutureTransactionsInCurrentMonth() {
        LocalDate midMonthReference = LocalDate.of(2026, 6, 15);
        Account ordinary = account(70L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(71L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(74L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                midMonthReference, 1, PersonalBank.SHINHAN_BANK, ordinary, installment, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(midMonthReference)));

        Map<YearMonth, Long> monthlyCounts = generated.transactions().stream()
                .collect(Collectors.groupingBy(
                        transaction -> YearMonth.from(transaction.getTranDate()),
                        Collectors.counting()
                ));
        // 지난 2개월(4~5월)은 완결된 달이라 102건씩, 현재 월(6월 15일까지)은 102건보다 적어야 한다.
        assertEquals(102L, monthlyCounts.get(YearMonth.of(2026, 4)));
        assertEquals(102L, monthlyCounts.get(YearMonth.of(2026, 5)));
        assertTrue(monthlyCounts.get(YearMonth.of(2026, 6)) < 102L);
        assertTrue(generated.transactions().size() >= 204 && generated.transactions().size() < 306);
    }

    @Test
    void shiftsThreeMonthWindowWithReferenceDate() {
        LocalDate referenceDate = LocalDate.of(2026, 11, 30);
        Account ordinary = account(72L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(73L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(75L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                referenceDate, 1, PersonalBank.SHINHAN_BANK, ordinary, installment, loan
        );

        Set<YearMonth> months = generated.transactions().stream()
                .map(transaction -> YearMonth.from(transaction.getTranDate()))
                .collect(Collectors.toSet());
        assertEquals(Set.of(YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11)), months);
    }

    @Test
    void splitsLoanRepaymentIntoPrincipalAndInterest() {
        Account ordinary = account(80L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(82L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(81L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                REFERENCE_DATE, 26, PersonalBank.NH_BANK, ordinary, installment, loan
        );

        List<AccountTransaction> loanTransactions = generated.transactions().stream()
                .filter(transaction -> transaction.getTransactionCategory() == AccountTransactionCategory.LOAN)
                .toList();
        assertEquals(3, loanTransactions.size());
        for (AccountTransaction transaction : loanTransactions) {
            assertEquals("원리금상환", transaction.getLoanTransactionTypeName());
            assertNotNull(transaction.getLoanPrincipalAmount());
            assertNotNull(transaction.getLoanInterestAmount());
            assertEquals(transaction.getOutAmount(),
                    transaction.getLoanPrincipalAmount().add(transaction.getLoanInterestAmount()));
        }
    }

    @Test
    void handlesFirstDayOfMonthAsReferenceDateWithMinimalCurrentMonthTransactions() {
        LocalDate firstOfMonth = LocalDate.of(2026, 6, 1);
        Account ordinary = account(90L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(91L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(98L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                firstOfMonth, 1, PersonalBank.SHINHAN_BANK, ordinary, installment, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(firstOfMonth)));
        long currentMonthCount = generated.transactions().stream()
                .filter(transaction -> YearMonth.from(transaction.getTranDate()).equals(YearMonth.of(2026, 6)))
                .count();
        assertTrue(currentMonthCount > 0 && currentMonthCount < 10,
                "월 1일에는 지난달 102건씩 + 이번 달 소수 건만 있어야 한다: " + currentMonthCount);
    }

    @Test
    void handlesLeapYearFebruary29AsReferenceDate() {
        LocalDate leapDay = LocalDate.of(2028, 2, 29);
        Account ordinary = account(92L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(99L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(93L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                leapDay, 26, PersonalBank.NH_BANK, ordinary, installment, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(leapDay)));
        assertTrue(generated.transactions().size() >= 204);
    }

    @Test
    void handlesNonLeapYearFebruary28AsReferenceDate() {
        LocalDate lastFebDay = LocalDate.of(2027, 2, 28);
        Account ordinary = account(94L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(95L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(101L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                lastFebDay, 1, PersonalBank.SHINHAN_BANK, ordinary, installment, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(lastFebDay)));
        // 2월은 완결된 달이라도 102건이 아니라 28일치 패턴 그대로다.
        Map<YearMonth, Long> monthlyCounts = generated.transactions().stream()
                .collect(Collectors.groupingBy(
                        transaction -> YearMonth.from(transaction.getTranDate()),
                        Collectors.counting()
                ));
        assertEquals(102L, monthlyCounts.get(YearMonth.of(2027, 2)));
    }

    @Test
    void handlesThirtyOneDayMonthEndAsReferenceDate() {
        LocalDate endOfJuly = LocalDate.of(2026, 7, 31);
        Account ordinary = account(96L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(102L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(97L, AccountGroup.LOAN);

        GeneratedTransactions generated = generator.generate(
                endOfJuly, 26, PersonalBank.NH_BANK, ordinary, installment, loan
        );

        assertTrue(generated.transactions().stream()
                .noneMatch(transaction -> transaction.getTranDate().isAfter(endOfJuly)));
        assertEquals(306, generated.transactions().size());
    }

    @Test
    void storesDepositNamesInsteadOfPlatformDisplayNamesInEveryTransaction() {
        for (int userOrdinal = 1; userOrdinal <= 4; userOrdinal++) {
            GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                    userOrdinal, PersonalBank.SHINHAN_BANK,
                    account(100L + userOrdinal, AccountGroup.DEPOSIT_TRUST),
                    account(110L + userOrdinal, AccountGroup.DEPOSIT_TRUST),
                    account(120L + userOrdinal, AccountGroup.LOAN)
            );
            assertTrue(generated.transactions().stream()
                    .noneMatch(transaction ->
                            transaction.getDesc3() != null
                                    && PLATFORM_DISPLAY_NAME_TOKENS.stream()
                                    .anyMatch(transaction.getDesc3()::contains)),
                    "userOrdinal=" + userOrdinal);
        }
    }

    @Test
    void generatesAllConfirmedSeedDepositNamesAcrossFiftyUsers() {
        Set<String> observedIncomeCounterparties = new java.util.HashSet<>();
        for (int userOrdinal = 1; userOrdinal <= 50; userOrdinal++) {
            GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                    userOrdinal, PersonalBank.SHINHAN_BANK,
                    account(200L + userOrdinal, AccountGroup.DEPOSIT_TRUST),
                    account(300L + userOrdinal, AccountGroup.DEPOSIT_TRUST),
                    account(400L + userOrdinal, AccountGroup.LOAN)
            );
            generated.transactions().stream()
                    .filter(transaction -> transaction.getInAmount().signum() > 0)
                    .map(AccountTransaction::getDesc3)
                    .filter(INCOME_COUNTERPARTY_NAMES::contains)
                    .forEach(observedIncomeCounterparties::add);
        }
        assertEquals(INCOME_COUNTERPARTY_NAMES, observedIncomeCounterparties);
    }

    @Test
    void completedMonthsMeetDifficultyRatioRanges() {
        Account ordinary = account(120L, AccountGroup.DEPOSIT_TRUST);
        Account installment = account(121L, AccountGroup.DEPOSIT_TRUST);
        Account loan = account(122L, AccountGroup.LOAN);

        // REFERENCE_DATE가 월말이라 세 달(4~6월) 모두 완결된 달로 취급된다.
        GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                1, PersonalBank.SHINHAN_BANK, ordinary, installment, loan
        );

        Map<YearMonth, Map<Difficulty, Long>> byMonth =
                generated.transactions().stream()
                        .filter(VirtualFinancialTransactionGeneratorTest::isConsumption)
                        .collect(Collectors.groupingBy(
                                transaction -> YearMonth.from(transaction.getTranDate()),
                                Collectors.groupingBy(
                                        VirtualFinancialTransactionGeneratorTest::difficultyOf,
                                        Collectors.counting()
                                )
                        ));

        assertEquals(3, byMonth.size());
        byMonth.forEach((month, counts) -> {
            long total = counts.values().stream().mapToLong(Long::longValue).sum();
            assertEquals(74L, total, month.toString());
            assertRatioInRange(counts, Difficulty.EASY, total, 0.20, 0.30);
            assertRatioInRange(counts, Difficulty.MEDIUM, total, 0.50, 0.60);
            assertRatioInRange(counts, Difficulty.HARD, total, 0.15, 0.20);
            assertRatioInRange(counts, Difficulty.UNDECIDABLE, total, 0.05, 0.10);
        });
    }

    @Test
    void everyConsumerProfileIncludesAllFourDifficulties() {
        for (int userOrdinal = 1; userOrdinal <= 4; userOrdinal++) {
            GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                    userOrdinal, PersonalBank.SHINHAN_BANK,
                    account(130L + userOrdinal, AccountGroup.DEPOSIT_TRUST),
                    account(140L + userOrdinal, AccountGroup.DEPOSIT_TRUST),
                    account(150L + userOrdinal, AccountGroup.LOAN)
            );
            Set<Difficulty> difficulties =
                    generated.transactions().stream()
                            .filter(VirtualFinancialTransactionGeneratorTest::isConsumption)
                            .map(VirtualFinancialTransactionGeneratorTest::difficultyOf)
                            .collect(Collectors.toSet());
            assertEquals(
                    Set.of(Difficulty.values()), difficulties,
                    "userOrdinal=" + userOrdinal
            );
        }
    }

    @Test
    void undecidableDifficultyDoesNotExposeCategorySpecificAmountOrPaymentMethod() {
        GeneratedTransactions generated = generator.generate(REFERENCE_DATE,
                2, PersonalBank.KEB_HANA_BANK,
                account(160L, AccountGroup.DEPOSIT_TRUST), account(161L, AccountGroup.DEPOSIT_TRUST),
                account(162L, AccountGroup.LOAN)
        );

        List<AccountTransaction> undecidable = generated.transactions().stream()
                        .filter(transaction -> UNDECIDABLE_NAMES.contains(transaction.getDesc3()))
                        .toList();
        assertTrue(!undecidable.isEmpty());
        assertTrue(undecidable.stream()
                .allMatch(transaction -> "체크카드".equals(transaction.getDesc2())));
        assertTrue(undecidable.stream()
                .allMatch(transaction -> transaction.getOutAmount().longValue() >= 5_000L
                        && transaction.getOutAmount().longValue() <= 100_000L));
    }

    private static void assertRatioInRange(
            Map<Difficulty, Long> counts,
            Difficulty difficulty,
            long total, double minRatio, double maxRatio) {
        long count = counts.getOrDefault(difficulty, 0L);
        double ratio = (double) count / total;
        assertTrue(ratio >= minRatio && ratio <= maxRatio,
                difficulty + " ratio=" + ratio + " count=" + count + " total=" + total);
    }

    private static boolean isConsumption(AccountTransaction transaction) {
        return transaction.getOutAmount().signum() > 0 && "신한체".equals(transaction.getDesc2());
    }

    private static Difficulty difficultyOf(AccountTransaction transaction) {
        String name = transaction.getDesc3();
        if (UNDECIDABLE_NAMES.contains(name)) {
            return Difficulty.UNDECIDABLE;
        }
        if (HARD_NAMES.contains(name)) {
            return Difficulty.HARD;
        }
        if (name != null && MEDIUM_PREFIXES.stream().anyMatch(name::startsWith)) {
            return Difficulty.MEDIUM;
        }
        return Difficulty.EASY;
    }

    private enum Difficulty {
        EASY, MEDIUM, HARD, UNDECIDABLE
    }

    private static long countCategory(List<AccountTransaction> transactions,
                                      AccountTransactionCategory category) {
        return transactions.stream()
                .filter(transaction -> transaction.getTransactionCategory() == category)
                .count();
    }

    private static void verifyAccountBalance(Account account, GeneratedTransactions generated) {
        account.setBalance(generated.finalBalances().get(account.getId()));
        List<AccountTransaction> accountTransactions = generated.transactions().stream()
                .filter(transaction -> account.getId().equals(transaction.getAccountId()))
                .toList();
        AccountBalanceConsistencyValidator.validate(account, accountTransactions);
    }

    // 난이도 "보통" 등급은 PG사 접두어를 붙여서 표시하므로 contains로 비교해야 EASY/MEDIUM 등급 표기를 모두 잡아낸다.
    // HARD/UNDECIDABLE 등급은 상호명을 완전히 다른 이름으로 대체하므로 매치되지 않는 게 정상이다.
    private static boolean hasMerchant(GeneratedTransactions generated, String merchant) {
        return generated.transactions().stream()
                .anyMatch(transaction -> transaction.getDesc3() != null && transaction.getDesc3().contains(merchant));
    }

    private static double averageCardSpending(GeneratedTransactions generated) {
        return generated.transactions().stream()
                .filter(transaction -> "신한체".equals(transaction.getDesc2()))
                .mapToDouble(transaction -> transaction.getOutAmount().doubleValue())
                .average()
                .orElseThrow();
    }

    private static List<AccountTransaction> insuranceTransactions(GeneratedTransactions generated) {
        return generated.transactions().stream()
                .filter(transaction -> INSURANCE_PRODUCTS.contains(transaction.getDesc3()))
                .toList();
    }

    private static Account account(Long id, AccountGroup group) {
        Account account = new Account();
        account.setId(id);
        account.setAccountGroup(group);
        return account;
    }
}
