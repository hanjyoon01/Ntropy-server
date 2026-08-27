package com.ntropy.account.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.ntropy.account.domain.AccountTransactionCategory;
import com.ntropy.account.domain.PersonalBank;
import com.ntropy.account.domain.TransactionFingerprint;
import com.ntropy.account.domain.entity.Account;
import com.ntropy.account.domain.entity.AccountTransaction;

/**
 * FIN-005 가상 금융 거래를 고정된 규칙으로 생성한다.
 * 거래 기간은 고정 날짜가 아니라 호출 시점에 주입되는 기준일(referenceDate)이 속한 현재 월과
 * 직전 2개월이다. 현재 월은 기준일까지만 생성하고 그 이후 날짜는 만들지 않는다.
 */
@Component
public class VirtualFinancialTransactionGenerator {

    /** 수시입출금(income+fixed+consumption+적금이체+대출상환) + 적금 1건 + 대출 1건. */
    public static final int TRANSACTIONS_PER_USER_PER_MONTH = 102;
    private static final int CONSUMPTION_TRANSACTIONS_PER_MONTH = 74;

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 가상 소득 입금 거래에 사용할 정산 주체명. 시연용으로 메인 직업(배달: 우아한청년들·쿠팡이츠정산·위대한상상)과
     * 서브 직업(대리: 카카오모빌리티, 펫시터: 펫피플)의 정산처만 고정한다(#225).
     * work-service의 PLATFORM.deposit_name 확정 시드 데이터(services/work-service/src/main/resources/db/work-service-seed.sql,
     * docs/financial-diagnosis-mvp-spec.md §3.6)에 실재하는 값의 부분집합이어야 한다. 두 서비스는 별도 모듈이라
     * 자동 동기화되지 않으니, 시드 데이터에서 해당 값이 바뀌거나 제거되면 이 배열도 함께 수정할 것.
     */
    private static final String[] INCOME_COUNTERPARTY_NAMES = {
            "우아한청년들", "쿠팡이츠정산", "위대한상상", "카카오모빌리티", "펫피플"
    };
    public static final int INCOME_COUNTERPARTY_COUNT = INCOME_COUNTERPARTY_NAMES.length;

    private static final FixedExpenseTemplate[] RECURRING_FIXED_EXPENSES = {
            fixedExpense("우리집월세", 650_000L, 850_000L),
            fixedExpense("KT통신요금", 55_000L, 70_000L),
            fixedExpense("국민건강보험", 80_000L, 110_000L),
            fixedExpense("한국전력", 45_000L, 85_000L),
            fixedExpense("서울도시가스", 35_000L, 70_000L),
            fixedExpense("티머니정기권", 65_000L, 65_000L),
            fixedExpense("넷플릭스", 17_000L, 17_000L),
            fixedExpense("멜론", 11_000L, 11_000L),
            fixedExpense("네이버클라우드", 5_000L, 5_000L)
    };

    /** 시연용으로 보험 상품 2종만 고정한다(#225). */
    private static final InsuranceProduct[] INSURANCE_PRODUCTS = {
            insurance("삼성생명", "삼성생명 실손보험", 45_000L, 80_000L),
            insurance("KB손해보험", "KB 이륜차 운전자보험", 20_000L, 40_000L)
    };

    private static final String[] BRANCHES = {
            "여의도", "강남", "서초동", "중소기업", "온천동", "동진주"
    };

    private static final String[] COUNTERPARTY_BANKS = {
            "기업", "국민", "농협", "우리", "하나", "신한", "케이"
    };

    private static final ConsumptionTemplate[] RATIONAL_CONSUMPTION = {
            spending("식비", 4_000L, 16_000L, "한솥도시락", "김밥천국", "동네분식"),
            spending("생활", 3_000L, 45_000L, "다이소", "이마트", "홈플러스"),
            spending("교통", 1_200L, 8_000L, "티머니", "서울교통공사"),
            spending("식비", 5_000L, 48_000L, "하나로마트", "동네마트", "쿠팡"),
            spending("의료건강", 4_000L, 28_000L, "온누리약국", "동네의원"),
            spending("교육", 5_000L, 32_000L, "알라딘중고서점", "교보문고"),
            spending("쇼핑", 8_000L, 55_000L, "쿠팡", "네이버쇼핑"),
            spending("문화여가", 8_000L, 25_000L, "CGV조조", "메가박스조조")
    };

    private static final ConsumptionTemplate[] TREND_CONSUMPTION = {
            spending("패션", 25_000L, 90_000L, "무신사", "29CM", "W컨셉"),
            spending("명품뷰티", 20_000L, 100_000L, "신세계백화점", "현대백화점", "올리브영"),
            spending("디지털", 30_000L, 150_000L, "애플스토어", "삼성스토어"),
            spending("카페", 6_000L, 20_000L, "스타벅스", "블루보틀", "런던베이글뮤지엄"),
            spending("외식", 25_000L, 70_000L, "캐치테이블", "아웃백", "오마카세서울"),
            spending("문화여가", 20_000L, 90_000L, "인터파크티켓", "YES24티켓"),
            spending("이동", 12_000L, 55_000L, "카카오모빌리티", "쏘카"),
            spending("인테리어", 20_000L, 90_000L, "오늘의집", "이케아")
    };

    private static final ConsumptionTemplate[] VALUE_CONSUMPTION = {
            spending("취향식음", 9_000L, 45_000L, "테라로사", "프릳츠커피", "비건베이커리"),
            spending("독서", 12_000L, 55_000L, "교보문고", "독립서점", "알라딘"),
            spending("배움", 25_000L, 140_000L, "클래스101", "탈잉", "공방원데이클래스"),
            spending("문화예술", 18_000L, 110_000L, "예술의전당", "국립현대미술관", "인터파크티켓"),
            spending("친환경", 10_000L, 65_000L, "제로웨이스트샵", "생협", "리필스테이션"),
            spending("반려생활", 15_000L, 95_000L, "동물병원", "반려생활", "펫프렌즈"),
            spending("건강", 12_000L, 100_000L, "요가원", "필라테스", "한살림"),
            spending("후원", 10_000L, 50_000L, "유니세프", "동물자유연대", "아름다운재단")
    };

    private static final ConsumptionTemplate[] IMPULSE_CONSUMPTION = {
            spending("배달", 12_000L, 75_000L, "우아한형제들", "쿠팡이츠", "위대한상상"),
            spending("편의점", 2_000L, 38_000L, "CU", "GS25", "세븐일레븐"),
            spending("모바일", 3_000L, 120_000L, "구글플레이", "애플앱스토어", "카카오이모티콘"),
            spending("즉흥쇼핑", 8_000L, 190_000L, "쿠팡", "네이버쇼핑", "카카오선물하기"),
            spending("심야이동", 8_000L, 85_000L, "카카오모빌리티", "타다"),
            spending("간식", 3_000L, 35_000L, "스타벅스", "공차", "배스킨라빈스"),
            spending("오락", 5_000L, 95_000L, "PC방", "코인노래방", "넷마블"),
            spending("생활", 4_000L, 80_000L, "다이소", "오늘의집", "편의점택배")
    };

    /** FIN-102 소비 분류 난이도 "보통": PG사/간편결제명 접두어를 원 상호명 앞에 붙인다. */
    private static final String[] DIFFICULTY_MEDIUM_PREFIXES = {
            "KG이니시스_", "토스페이_", "네이버페이_", "카카오페이_", "나이스페이_", "NHN페이코_"
    };

    /** FIN-102 소비 분류 난이도 "어려움": 카테고리를 짐작할 수 없는 사업자명으로 상호명을 완전히 대체한다. */
    private static final String[] DIFFICULTY_HARD_NAMES = {
            "주식회사 더원", "에이치앤에스", "케이에스컴퍼니", "스마트로",
            "제이앤파트너스", "한올유통", "비앤에스코퍼레이션", "지오정보통신"
    };

    /** FIN-102 소비 분류 난이도 "판별 불가": 어떤 보조 정보로도 카테고리를 정할 수 없는 이름들. */
    private static final String[] DIFFICULTY_UNDECIDABLE_NAMES = {
            "김민수", "결제", "기타", "1234PAY"
    };

    /**
     * FIN-102 난이도 목표 비율(%). 합은 100이며 각각 이슈에서 정한 범위
     * (쉬움 20~30%, 보통 50~60%, 어려움 15~20%, 판별불가 5~10%) 안에 있다.
     * 74건 기준으로 largest-remainder 배분하면 18/40/12/4건이 나온다.
     */
    private static final double[] DIFFICULTY_TARGET_PERCENTAGES = {24.3, 54.1, 16.2, 5.4};

    public GeneratedTransactions generate(LocalDate referenceDate, int userOrdinal, PersonalBank bank,
                                          Account ordinaryAccount, Account installmentAccount, Account loanAccount) {
        return generate(referenceDate, userOrdinal, 0L, bank, ordinaryAccount, installmentAccount, loanAccount);
    }

    /** 공용 데이터셋 seed와 사용자 순번으로 소비 프로필을 결정해 거래를 생성한다. */
    public GeneratedTransactions generate(LocalDate referenceDate, int userOrdinal, long randomSeed,
                                          PersonalBank bank, Account ordinaryAccount,
                                          Account installmentAccount, Account loanAccount) {
        return generate(
                referenceDate, userOrdinal, consumerProfileFor(userOrdinal, randomSeed),
                bank, ordinaryAccount, installmentAccount, loanAccount
        );
    }

    /** 실제 로그인 사용자는 userId 해시로 네 소비 유형 중 하나에 안정적으로 배정한다. */
    public GeneratedTransactions generateForUser(LocalDate referenceDate, Long userId, PersonalBank bank,
                                                  Account ordinaryAccount, Account installmentAccount,
                                                  Account loanAccount) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다");
        }
        int hash = Long.hashCode(userId);
        ConsumerProfile profile = consumerProfileForUser(userId);
        ConsumerProfile[] profiles = ConsumerProfile.values();
        int variation = Math.floorMod(hash / profiles.length, 12);
        int stableUserOrdinal = 1 + profile.ordinal() + profiles.length * variation;
        return generate(
                referenceDate, stableUserOrdinal, profile, bank, ordinaryAccount, installmentAccount, loanAccount
        );
    }

    private GeneratedTransactions generate(LocalDate referenceDate, int userOrdinal, ConsumerProfile consumerProfile,
                                            PersonalBank bank, Account ordinaryAccount,
                                            Account installmentAccount, Account loanAccount) {
        requireAccountId(ordinaryAccount);
        requireAccountId(installmentAccount);
        requireAccountId(loanAccount);
        if (referenceDate == null) {
            throw new IllegalArgumentException("기준일이 필요합니다");
        }
        if (userOrdinal < 1) {
            throw new IllegalArgumentException("가상 사용자 순번이 범위를 벗어났습니다: " + userOrdinal);
        }

        int userIncomeCounterpartyCount = userIncomeCounterpartyCount(userOrdinal);
        List<String> incomeCounterparties = incomeCounterparties(userOrdinal, userIncomeCounterpartyCount);

        List<PlannedTransaction> ordinaryPlans = new ArrayList<>();
        List<PlannedTransaction> installmentPlans = new ArrayList<>();
        List<PlannedTransaction> loanPlans = new ArrayList<>();

        YearMonth currentMonth = YearMonth.from(referenceDate);
        for (int monthOffset = 0; monthOffset < 3; monthOffset++) {
            YearMonth month = currentMonth.minusMonths(2 - monthOffset);
            boolean isCurrentMonth = monthOffset == 2;

            List<PlannedTransaction> monthOrdinaryPlans = new ArrayList<>();
            List<PlannedTransaction> monthInstallmentPlans = new ArrayList<>();
            List<PlannedTransaction> monthLoanPlans = new ArrayList<>();
            addIncomePlans(monthOrdinaryPlans, userOrdinal, monthOffset, month, incomeCounterparties);
            addFixedExpensePlans(monthOrdinaryPlans, userOrdinal, monthOffset, month);
            addConsumptionPlans(monthOrdinaryPlans, userOrdinal, monthOffset, month, consumerProfile);
            addInstallmentTransferPlans(monthOrdinaryPlans, monthInstallmentPlans, userOrdinal, month);
            addLoanRepaymentPlans(monthOrdinaryPlans, monthLoanPlans, userOrdinal, month);

            if (isCurrentMonth) {
                // 현재 월은 기준일 이후 거래를 만들지 않는다. 지난 2개월은 항상 완결된 달이라 필터가 필요 없다.
                monthOrdinaryPlans.removeIf(plan -> plan.date().isAfter(referenceDate));
                monthInstallmentPlans.removeIf(plan -> plan.date().isAfter(referenceDate));
                monthLoanPlans.removeIf(plan -> plan.date().isAfter(referenceDate));
            }
            ordinaryPlans.addAll(monthOrdinaryPlans);
            installmentPlans.addAll(monthInstallmentPlans);
            loanPlans.addAll(monthLoanPlans);
        }

        List<AccountTransaction> ordinaryTransactions = materialize(
                ordinaryAccount.getId(), AccountTransactionCategory.ORDINARY,
                BigDecimal.valueOf(3_000_000L + userOrdinal * 20_000L), bank, ordinaryPlans
        );
        List<AccountTransaction> installmentTransactions = materialize(
                installmentAccount.getId(), AccountTransactionCategory.INSTALLMENT, ZERO, bank, installmentPlans
        );
        List<AccountTransaction> loanTransactions = materialize(
                loanAccount.getId(), AccountTransactionCategory.LOAN,
                BigDecimal.valueOf(15_000_000L + userOrdinal * 100_000L), bank, loanPlans
        );

        List<AccountTransaction> all = new ArrayList<>(
                ordinaryTransactions.size() + installmentTransactions.size() + loanTransactions.size()
        );
        all.addAll(ordinaryTransactions);
        all.addAll(installmentTransactions);
        all.addAll(loanTransactions);

        // 지난 2개월은 완결된 달이라 항상 월 102건씩 204건이고, 현재 월은 기준일까지만 생성돼 0~102건이다.
        int minimumExpected = TRANSACTIONS_PER_USER_PER_MONTH * 2;
        int maximumExpected = TRANSACTIONS_PER_USER_PER_MONTH * 3;
        if (all.size() < minimumExpected || all.size() > maximumExpected) {
            throw new IllegalStateException(
                    "가상 거래 건수 불일치: expected=[" + minimumExpected + "," + maximumExpected + "], actual=" + all.size()
            );
        }

        Map<Long, BigDecimal> finalBalances = new LinkedHashMap<>();
        finalBalances.put(ordinaryAccount.getId(), lastBalance(ordinaryTransactions));
        finalBalances.put(installmentAccount.getId(), lastBalance(installmentTransactions));
        finalBalances.put(loanAccount.getId(), lastBalance(loanTransactions));
        return new GeneratedTransactions(
                List.copyOf(all), Map.copyOf(finalBalances), userIncomeCounterpartyCount
        );
    }

    private static void addIncomePlans(List<PlannedTransaction> plans, int userOrdinal, int monthOffset,
                                       YearMonth month, List<String> incomeCounterparties) {
        long mainIncome = 3_600_000L + userOrdinal * 10_000L + monthOffset * 20_000L;
        plans.add(income(month.atDay(1), LocalTime.of(6, 0), mainIncome,
                incomeCounterparties.get(0), "정산입금"));

        int[] weeklyDays = {6, 13, 20, 27};
        for (int i = 0; i < weeklyDays.length; i++) {
            long amount = 190_000L + ((userOrdinal + monthOffset + i) % 7) * 10_000L;
            plans.add(income(month.atDay(weeklyDays[i]), LocalTime.of(6, 10 + i), amount,
                    incomeCounterparties.get(1), "정산입금"));
        }

        int[] irregularDays = {4, 9, 16, 22, 27};
        for (int i = 0; i < irregularDays.length; i++) {
            long amount = 100_000L + ((userOrdinal * 3L + monthOffset + i) % 6) * 10_000L;
            String irregularCounterparty = incomeCounterparties.get(
                    1 + i % (incomeCounterparties.size() - 1)
            );
            plans.add(income(month.atDay(irregularDays[i]), LocalTime.of(6, 20 + i), amount,
                    irregularCounterparty, "정산입금"));
        }

        plans.add(nonJobIncome(month.atDay(11), LocalTime.of(7, 10), 32_000L,
                "쿠팡", "카드취소", "쇼핑 환불"));
        plans.add(nonJobIncome(month.atDay(24), LocalTime.of(7, 20), 18_500L,
                "김밥천국", "카드취소", "식비 환불"));
        plans.add(nonJobIncome(month.atDay(15), LocalTime.of(7, 30), 4_800L,
                "NH카드캐쉬백", "캐시백", "카드 캐시백"));
        plans.add(nonJobIncome(month.atEndOfMonth(), LocalTime.of(23, 50), 1_200L + userOrdinal * 10L,
                "예금이자", "예금이자", "결산이자"));
    }

    private static PlannedTransaction income(LocalDate date, LocalTime time, long amount,
                                              String counterparty, String method) {
        return new PlannedTransaction(
                date, time, ZERO, BigDecimal.valueOf(amount),
                "입금", method, counterparty, "소득", "정산은행"
        );
    }

    private static PlannedTransaction nonJobIncome(LocalDate date, LocalTime time, long amount,
                                                    String counterparty, String method, String memo) {
        return new PlannedTransaction(
                date, time, ZERO, BigDecimal.valueOf(amount),
                "입금", method, counterparty, memo, "입금은행"
        );
    }

    private static void addFixedExpensePlans(List<PlannedTransaction> plans, int userOrdinal,
                                             int monthOffset, YearMonth month) {
        int insuranceCount = userInsuranceCount(userOrdinal);
        int recurringExpenseCount = 10 - insuranceCount;
        int slot = 0;

        for (int i = 0; i < recurringExpenseCount; i++, slot++) {
            FixedExpenseTemplate expense = RECURRING_FIXED_EXPENSES[i];
            long amount = deterministicAmount(
                    expense.minimumAmount(), expense.maximumAmount(), userOrdinal, monthOffset, i
            );
            plans.add(new PlannedTransaction(
                    month.atDay(2 + slot * 2), LocalTime.of(8, slot), BigDecimal.valueOf(amount), ZERO,
                    "출금", "자동납부", expense.name(), "고정비", "자동이체은행"
            ));
        }

        for (int i = 0; i < insuranceCount; i++, slot++) {
            InsuranceProduct insurance = insuranceProduct(userOrdinal, i);
            long amount = deterministicAmount(
                    insurance.minimumAmount(), insurance.maximumAmount(), userOrdinal, 0, i + 20
            );
            plans.add(new PlannedTransaction(
                    month.atDay(2 + slot * 2), LocalTime.of(8, slot), BigDecimal.valueOf(amount), ZERO,
                    "출금", "자동납부", insurance.productName(), "보험료", "자동이체은행"
            ));
        }
    }

    private static void addConsumptionPlans(List<PlannedTransaction> plans, int userOrdinal,
                                             int monthOffset, YearMonth month, ConsumerProfile profile) {
        ConsumptionDifficulty[] difficulties = difficultyAssignments(
                userOrdinal, monthOffset, CONSUMPTION_TRANSACTIONS_PER_MONTH
        );
        for (int i = 0; i < CONSUMPTION_TRANSACTIONS_PER_MONTH; i++) {
            int day = 1 + i % 28;
            int hour = 10 + (i / 28) * 4 + (i % 3);
            int minute = (userOrdinal * 7 + monthOffset * 13 + i * 17) % 60;
            ConsumptionTemplate template = consumptionTemplate(profile, i + monthOffset);
            String merchant = template.merchants[
                    (userOrdinal + monthOffset + i) % template.merchants.length
            ];
            ConsumptionDifficulty difficulty = difficulties[i];
            long amount = difficulty == ConsumptionDifficulty.UNDECIDABLE
                    ? undecidableAmount(userOrdinal, monthOffset, i)
                    : consumptionAmount(template, userOrdinal, monthOffset, i);
            String method = difficulty == ConsumptionDifficulty.UNDECIDABLE
                    ? "체크카드"
                    : merchant.equals("티머니") || merchant.equals("서울교통공사")
                    ? "교통카드" : paymentMethod(profile, i);
            String displayName = applyDifficulty(merchant, difficulty, userOrdinal, monthOffset, i);
            plans.add(new PlannedTransaction(
                    month.atDay(day), LocalTime.of(hour, minute, i % 60), BigDecimal.valueOf(amount), ZERO,
                    "출금", method, displayName, template.label, "카드사"
            ));
        }
    }

    private static long consumptionAmount(ConsumptionTemplate template, int userOrdinal,
                                          int monthOffset, int index) {
        long rawAmount = template.minimumAmount + Math.floorMod(
                userOrdinal * 7_919L + monthOffset * 1_237L + index * 3_571L,
                template.maximumAmount - template.minimumAmount + 1L
        );
        return (rawAmount / 100L) * 100L;
    }

    /** 판별 불가 거래는 원 카테고리의 금액 범위를 사용하지 않아 금액으로 카테고리를 역추론할 수 없게 한다. */
    private static long undecidableAmount(int userOrdinal, int monthOffset, int index) {
        long rawAmount = 5_000L + Math.floorMod(
                userOrdinal * 5_003L + monthOffset * 2_003L + index * 7_001L,
                95_001L
        );
        return (rawAmount / 100L) * 100L;
    }

    /** 상호명 표시를 난이도에 맞춰 치환한다. */
    private static String applyDifficulty(String merchant, ConsumptionDifficulty difficulty,
                                          int userOrdinal, int monthOffset, int index) {
        return switch (difficulty) {
            case EASY -> merchant;
            case MEDIUM -> DIFFICULTY_MEDIUM_PREFIXES[
                    Math.floorMod(userOrdinal + monthOffset + index, DIFFICULTY_MEDIUM_PREFIXES.length)
                    ] + merchant;
            case HARD -> DIFFICULTY_HARD_NAMES[
                    Math.floorMod(userOrdinal * 3 + monthOffset + index, DIFFICULTY_HARD_NAMES.length)
                    ];
            case UNDECIDABLE -> DIFFICULTY_UNDECIDABLE_NAMES[
                    Math.floorMod(userOrdinal * 5 + monthOffset * 2 + index, DIFFICULTY_UNDECIDABLE_NAMES.length)
                    ];
        };
    }

    /**
     * total건에 대해 {@link #DIFFICULTY_TARGET_PERCENTAGES} 비율대로 난이도를 배분한 뒤,
     * total과 서로소인 stride로 인덱스를 치환해 월 앞부분에 특정 난이도가 몰리지 않게 섞는다.
     * 동일 userOrdinal·monthOffset·total이면 항상 같은 배열을 반환한다(결정적).
     */
    private static ConsumptionDifficulty[] difficultyAssignments(int userOrdinal, int monthOffset, int total) {
        int[] counts = difficultyCounts(total);
        ConsumptionDifficulty[] values = ConsumptionDifficulty.values();
        ConsumptionDifficulty[] base = new ConsumptionDifficulty[total];
        int slot = 0;
        for (int level = 0; level < values.length; level++) {
            for (int c = 0; c < counts[level]; c++) {
                base[slot++] = values[level];
            }
        }

        int stride = coprimeStride(total, userOrdinal, monthOffset);
        int seed = Math.floorMod(userOrdinal * 11 + monthOffset * 5, Math.max(total, 1));
        ConsumptionDifficulty[] shuffled = new ConsumptionDifficulty[total];
        for (int i = 0; i < total; i++) {
            shuffled[i] = base[Math.floorMod(i * stride + seed, total)];
        }
        return shuffled;
    }

    /** {@link #DIFFICULTY_TARGET_PERCENTAGES}를 largest-remainder 방식으로 total건에 배분한다. */
    private static int[] difficultyCounts(int total) {
        int levelCount = DIFFICULTY_TARGET_PERCENTAGES.length;
        double[] exact = new double[levelCount];
        int[] counts = new int[levelCount];
        int allocated = 0;
        for (int level = 0; level < levelCount; level++) {
            exact[level] = DIFFICULTY_TARGET_PERCENTAGES[level] * total / 100.0;
            counts[level] = (int) exact[level];
            allocated += counts[level];
        }
        List<Integer> byRemainderDesc = new ArrayList<>();
        for (int level = 0; level < levelCount; level++) {
            byRemainderDesc.add(level);
        }
        byRemainderDesc.sort((a, b) -> Double.compare(
                (exact[b] - counts[b]), (exact[a] - counts[a])
        ));
        int remaining = total - allocated;
        for (int i = 0; i < remaining; i++) {
            counts[byRemainderDesc.get(i % levelCount)]++;
        }
        return counts;
    }

    private static int coprimeStride(int total, int userOrdinal, int monthOffset) {
        if (total <= 1) {
            return 1;
        }
        int candidate = 2 + Math.floorMod(userOrdinal * 5 + monthOffset * 3, Math.max(total - 2, 1));
        while (gcd(candidate, total) != 1) {
            candidate++;
        }
        return candidate;
    }

    private static int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }

    private static void addInstallmentTransferPlans(List<PlannedTransaction> ordinaryPlans,
                                                     List<PlannedTransaction> installmentPlans,
                                                     int userOrdinal, YearMonth month) {
        long amount = 300_000L + (userOrdinal % 3) * 50_000L;
        LocalDate date = DefaultPaymentSchedule.occurrenceIn(month);

        ordinaryPlans.add(new PlannedTransaction(
                date, LocalTime.of(21, 0), BigDecimal.valueOf(amount), ZERO,
                "출금", "계좌이체", "정기적금", "적금 납입", "동일은행"
        ));
        installmentPlans.add(new PlannedTransaction(
                date, LocalTime.of(21, 1), ZERO, BigDecimal.valueOf(amount),
                "입금", "자동이체", "수시입출금계좌", "정기 납입", "동일은행"
        ));
    }

    private static void addLoanRepaymentPlans(List<PlannedTransaction> ordinaryPlans,
                                              List<PlannedTransaction> loanPlans,
                                              int userOrdinal, YearMonth month) {
        long amount = 450_000L + (userOrdinal % 4) * 50_000L;
        LocalDate date = DefaultPaymentSchedule.occurrenceIn(month);

        ordinaryPlans.add(new PlannedTransaction(
                date, LocalTime.of(21, 2), BigDecimal.valueOf(amount), ZERO,
                "출금", "계좌이체", "대출상환", "원리금 상환", "동일은행"
        ));
        loanPlans.add(new PlannedTransaction(
                date, LocalTime.of(21, 3), BigDecimal.valueOf(amount), ZERO,
                "상환", "자동이체", "대출계좌", "원리금 상환", "동일은행"
        ));
    }

    private static List<AccountTransaction> materialize(Long accountId, AccountTransactionCategory category,
                                                        BigDecimal openingBalance, PersonalBank bank,
                                                        List<PlannedTransaction> plans) {
        plans.sort(Comparator.comparing(PlannedTransaction::date).thenComparing(PlannedTransaction::time));
        List<AccountTransaction> result = new ArrayList<>(plans.size());
        BigDecimal balance = openingBalance;

        for (PlannedTransaction plan : plans) {
            balance = balance.add(plan.inAmount()).subtract(plan.outAmount());
            if (balance.signum() < 0) {
                throw new IllegalStateException("가상 거래 잔액이 음수입니다: accountId=" + accountId
                        + ", date=" + plan.date());
            }
            Descriptions descriptions = descriptions(bank, plan);
            AccountTransaction transaction = new AccountTransaction();
            transaction.setAccountId(accountId);
            transaction.setTransactionCategory(category);
            transaction.setTranDate(plan.date());
            transaction.setTranTime(plan.time());
            transaction.setOutAmount(plan.outAmount());
            transaction.setInAmount(plan.inAmount());
            if (category == AccountTransactionCategory.LOAN && plan.outAmount().signum() > 0) {
                transaction.setLoanTransactionTypeName("원리금상환");
                BigDecimal principal = loanPrincipalPortion(plan.outAmount());
                transaction.setLoanPrincipalAmount(principal);
                transaction.setLoanInterestAmount(plan.outAmount().subtract(principal));
            }
            transaction.setAfterBalance(balance);
            transaction.setDesc1(descriptions.desc1());
            transaction.setDesc2(descriptions.desc2());
            transaction.setDesc3(descriptions.desc3());
            transaction.setDesc4(descriptions.desc4());
            transaction.setFingerprint(fingerprint(transaction));
            result.add(transaction);
        }
        return result;
    }

    /** 원리금 상환액의 80%를 원금, 나머지를 이자로 결정적으로 분리한다. 둘을 더하면 항상 원상환액과 같다. */
    private static BigDecimal loanPrincipalPortion(BigDecimal repaymentAmount) {
        return repaymentAmount.multiply(BigDecimal.valueOf(8))
                .divide(BigDecimal.TEN, 0, java.math.RoundingMode.HALF_UP);
    }

    private static String fingerprint(AccountTransaction transaction) {
        if (transaction.getTransactionCategory() == AccountTransactionCategory.ORDINARY) {
            return TransactionFingerprint.hash(
                    transaction.getAccountId(), transaction.getTranDate(), transaction.getTranTime(),
                    transaction.getOutAmount(), transaction.getInAmount(), transaction.getAfterBalance(),
                    transaction.getDesc2(), transaction.getDesc3(), transaction.getDesc4()
            );
        }
        return TransactionFingerprint.hash(
                transaction.getAccountId(), transaction.getTransactionCategory(),
                transaction.getTranDate(), transaction.getTranTime(),
                transaction.getOutAmount(), transaction.getInAmount(), transaction.getAfterBalance(),
                transaction.getDesc2(), transaction.getDesc3(), transaction.getDesc4()
        );
    }

    private static Descriptions descriptions(PersonalBank bank, PlannedTransaction plan) {
        String counterparty = plan.counterparty();
        String branch = BRANCHES[plan.date().getDayOfMonth() % BRANCHES.length];
        String counterpartyBank = COUNTERPARTY_BANKS[
                plan.date().getDayOfMonth() % COUNTERPARTY_BANKS.length
        ];
        return switch (bank) {
            case IBK_INDUSTRIAL_BANK -> new Descriptions(
                    ibkCounterpartyHolder(plan), ibkDescription2(plan), counterparty, null
            );
            case KB_KOOKMIN_BANK -> new Descriptions(null, kbDescription2(plan), counterparty, branch);
            case NH_BANK -> new Descriptions(
                    null, nhDescription2(plan), counterparty,
                    "농협 " + String.format(java.util.Locale.ROOT, "%06d", 900 + plan.date().getDayOfMonth())
            );
            case SC_BANK -> new Descriptions(null, isCard(plan) ? "C/C" : "인터넷", counterparty, branch);
            case JEONBUK_BANK -> new Descriptions(null, null, "홈)" + counterparty,
                    "전북(" + branch + ")");
            case KYONGNAM_BANK -> new Descriptions(null, gyeongnamDescription2(plan), counterparty,
                    branch + "지점");
            case SAEMAUL_GEUMGO -> new Descriptions(null, saemaulDescription2(plan), counterparty, null);
            case KEB_HANA_BANK -> new Descriptions(null, hanaDescription2(plan), counterparty,
                    isTransfer(plan) ? "인터넷뱅킹" : branch);
            case SHINHAN_BANK -> new Descriptions(null, shinhanDescription2(plan), counterparty,
                    "(" + counterpartyBank + ")");
        };
    }

    private static String ibkCounterpartyHolder(PlannedTransaction plan) {
        if (isCard(plan) || isInterest(plan)) {
            return null;
        }
        if (isPrivateInsurance(plan)) {
            return insuranceCompany(plan.counterparty());
        }
        return plan.counterparty();
    }

    private static String ibkDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "이자";
        }
        if (isCard(plan)) {
            return plan.method().equals("체크카드") || plan.method().equals("교통카드") ? "체크" : "BC";
        }
        if (isAutomatic(plan)) {
            return "펌뱅킹";
        }
        return plan.method().equals("정산입금") ? "펌이체" : "이체";
    }

    private static String kbDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "결산이자";
        }
        if (isCard(plan)) {
            return plan.method().equals("간편결제") ? "스마트출금" : "FBS출금";
        }
        if (isAutomatic(plan)) {
            return "CMS공동";
        }
        return "전자금융";
    }

    private static String nhDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "예금이자";
        }
        if (plan.method().equals("캐시백")) {
            return "IC캐쉬백";
        }
        if (isCard(plan)) {
            return "NHBC체크";
        }
        if (isAutomatic(plan)) {
            return isInsurance(plan) ? "보험료" : "센터일괄";
        }
        return plan.method().equals("정산입금") ? "실시간이체" : "NH올원뱅크";
    }

    private static String gyeongnamDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "예금결산";
        }
        return "대체";
    }

    private static String saemaulDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "이자";
        }
        if (plan.counterparty().contains("적금")) {
            return "적금";
        }
        return isCard(plan) ? "스마트뱅킹" : "인터넷뱅킹";
    }

    private static String hanaDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "예금이자";
        }
        if (isCard(plan)) {
            return plan.method();
        }
        if (isAutomatic(plan)) {
            return "자동이체";
        }
        return plan.counterpartyBank().equals("동일은행") ? "당행송금" : "타행이체";
    }

    private static String shinhanDescription2(PlannedTransaction plan) {
        if (isInterest(plan)) {
            return "이자";
        }
        if (isCard(plan)) {
            return plan.inAmount().signum() > 0 ? "SHC입" : "신한체";
        }
        if (isAutomatic(plan)) {
            return "OP이체";
        }
        return plan.method().equals("정산입금") ? "FB이체" : "타행IB";
    }

    private static boolean isCard(PlannedTransaction plan) {
        return plan.method().equals("체크카드")
                || plan.method().equals("교통카드")
                || plan.method().equals("간편결제")
                || plan.method().equals("카드취소")
                || plan.method().equals("캐시백");
    }

    private static boolean isInterest(PlannedTransaction plan) {
        return plan.method().equals("예금이자");
    }

    private static boolean isAutomatic(PlannedTransaction plan) {
        return plan.method().equals("자동납부") || plan.method().equals("자동이체");
    }

    private static boolean isInsurance(PlannedTransaction plan) {
        return isPrivateInsurance(plan) || plan.counterparty().equals("국민건강보험");
    }

    private static boolean isPrivateInsurance(PlannedTransaction plan) {
        return plan.memo().equals("보험료");
    }

    private static boolean isTransfer(PlannedTransaction plan) {
        return !isCard(plan) && !isInterest(plan);
    }

    static ConsumerProfile consumerProfileFor(int userOrdinal) {
        return consumerProfileFor(userOrdinal, 0L);
    }

    static ConsumerProfile consumerProfileFor(int userOrdinal, long randomSeed) {
        ConsumerProfile[] profiles = ConsumerProfile.values();
        int index = (int) Math.floorMod((userOrdinal - 1L) + randomSeed, (long) profiles.length);
        return profiles[index];
    }

    static ConsumerProfile consumerProfileForUser(Long userId) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자 ID는 양수여야 합니다");
        }
        ConsumerProfile[] profiles = ConsumerProfile.values();
        return profiles[Math.floorMod(Long.hashCode(userId), profiles.length)];
    }

    private static ConsumptionTemplate consumptionTemplate(ConsumerProfile profile, int index) {
        ConsumptionTemplate[] templates = switch (profile) {
            case RATIONAL_FRUGAL -> RATIONAL_CONSUMPTION;
            case TREND_STATUS -> TREND_CONSUMPTION;
            case VALUE_TASTE -> VALUE_CONSUMPTION;
            case IMPULSE_INDIFFERENT -> IMPULSE_CONSUMPTION;
        };
        return templates[Math.floorMod(index, templates.length)];
    }

    private static String paymentMethod(ConsumerProfile profile, int index) {
        int simplePaymentInterval = switch (profile) {
            case RATIONAL_FRUGAL -> 7;
            case TREND_STATUS -> 2;
            case VALUE_TASTE -> 4;
            case IMPULSE_INDIFFERENT -> 3;
        };
        return index % simplePaymentInterval == 0 ? "간편결제" : "체크카드";
    }

    private static ConsumptionTemplate spending(String label, long minimumAmount, long maximumAmount,
                                                 String... merchants) {
        return new ConsumptionTemplate(label, minimumAmount, maximumAmount, merchants);
    }

    private static FixedExpenseTemplate fixedExpense(String name, long minimumAmount, long maximumAmount) {
        return new FixedExpenseTemplate(name, minimumAmount, maximumAmount);
    }

    private static InsuranceProduct insurance(String company, String productName,
                                               long minimumAmount, long maximumAmount) {
        return new InsuranceProduct(company, productName, minimumAmount, maximumAmount);
    }

    private static int userInsuranceCount(int userOrdinal) {
        return userOrdinal % 2 == 0 ? 2 : 1;
    }

    private static InsuranceProduct insuranceProduct(int userOrdinal, int insuranceOrdinal) {
        int index = Math.floorMod(userOrdinal - 1 + insuranceOrdinal * 3, INSURANCE_PRODUCTS.length);
        return INSURANCE_PRODUCTS[index];
    }

    private static String insuranceCompany(String productName) {
        for (InsuranceProduct insurance : INSURANCE_PRODUCTS) {
            if (insurance.productName().equals(productName)) {
                return insurance.company();
            }
        }
        throw new IllegalArgumentException("지원하지 않는 가상 보험 상품입니다: " + productName);
    }

    private static long deterministicAmount(long minimumAmount, long maximumAmount,
                                            int userOrdinal, int monthOffset, int seedOffset) {
        if (minimumAmount == maximumAmount) {
            return minimumAmount;
        }
        long rawAmount = minimumAmount + Math.floorMod(
                userOrdinal * 7_919L + monthOffset * 1_237L + seedOffset * 3_571L,
                maximumAmount - minimumAmount + 1L
        );
        return (rawAmount / 100L) * 100L;
    }

    private static List<String> incomeCounterparties(int userOrdinal, int count) {
        List<String> result = new ArrayList<>(count);
        int firstCounterpartyIndex = Math.floorMod(userOrdinal - 1, INCOME_COUNTERPARTY_COUNT);
        for (int i = 0; i < count; i++) {
            int counterpartyIndex = Math.floorMod(
                    firstCounterpartyIndex + i * 3, INCOME_COUNTERPARTY_COUNT
            );
            result.add(INCOME_COUNTERPARTY_NAMES[counterpartyIndex]);
        }
        return result;
    }

    static int userIncomeCounterpartyCount(int userOrdinal) {
        return userOrdinal % 2 == 0 ? 3 : 2;
    }

    private static BigDecimal lastBalance(List<AccountTransaction> transactions) {
        if (transactions.isEmpty()) {
            return ZERO;
        }
        return transactions.get(transactions.size() - 1).getAfterBalance();
    }

    private static void requireAccountId(Account account) {
        if (account == null || account.getId() == null) {
            throw new IllegalArgumentException("저장된 가상계좌가 필요합니다");
        }
    }

    public record GeneratedTransactions(
            List<AccountTransaction> transactions,
            Map<Long, BigDecimal> finalBalances,
            int userIncomeCounterpartyCount
    ) {
    }

    private enum ConsumptionDifficulty {
        EASY, MEDIUM, HARD, UNDECIDABLE
    }

    private record PlannedTransaction(
            LocalDate date,
            LocalTime time,
            BigDecimal outAmount,
            BigDecimal inAmount,
            String type,
            String method,
            String counterparty,
            String memo,
            String counterpartyBank
    ) {
    }

    private record Descriptions(String desc1, String desc2, String desc3, String desc4) {
    }

    private record ConsumptionTemplate(
            String label,
            long minimumAmount,
            long maximumAmount,
            String[] merchants
    ) {
    }

    private record FixedExpenseTemplate(String name, long minimumAmount, long maximumAmount) {
    }

    private record InsuranceProduct(
            String company,
            String productName,
            long minimumAmount,
            long maximumAmount
    ) {
    }

    enum ConsumerProfile {
        RATIONAL_FRUGAL,
        TREND_STATUS,
        VALUE_TASTE,
        IMPULSE_INDIFFERENT
    }
}
