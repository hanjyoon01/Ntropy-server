package com.ntropy.account.api.domain;

import java.util.List;
import java.util.Locale;

/**
 * LOAN 거래구분명에서 대출금 지급(신규·실행·증액) 거래를 판정하는 단일 규칙입니다.
 * account-service의 월간 소비 집계·금융 납입 예정 조회(SQL)와 ai-service의
 * 일간 소비 분류 배치(Java)가 이 키워드 목록과 판정 의미를 함께 사용합니다.
 *
 * 판정 의미는 "공백 정규화 후 부분 문자열 포함"입니다. "실행"이 "대출실행"의
 * 부분 문자열이므로 "대출실행"은 목록에서 제외해도 판정 결과가 같습니다.
 */
public final class LoanDisbursementKeywords {

    public static final List<String> KEYWORDS = List.of(
            "신규",
            "실행",
            "증액"
    );

    private LoanDisbursementKeywords() {
    }

    /**
     * loanTransactionTypeName이 대출금 지급 거래를 나타내는 키워드를 포함하는지 판정합니다.
     * null/빈 문자열은 제외 사유가 없는 것으로 보고 false(정상 상환 후보)를 반환합니다.
     */
    public static boolean matches(String loanTransactionTypeName) {
        String normalized = normalize(loanTransactionTypeName);
        return KEYWORDS.stream().anyMatch(normalized::contains);
    }

    private static String normalize(String value) {
        if (value == null) {
            return "";
        }

        return value
                .replaceAll("\\s+", "")
                .toUpperCase(Locale.ROOT);
    }
}
