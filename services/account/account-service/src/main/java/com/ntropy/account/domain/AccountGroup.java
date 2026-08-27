package com.ntropy.account.domain;

/**
 * CODEF 보유계좌 조회 응답의 {@code data} 하위 계좌 종류별 배열 구분.
 * 펀드·보험은 전용 {@code ACCOUNT} 행을 생성하지 않으므로 값을 두지 않는다.
 * 이 목록에 없는 응답 영역({@code resFund}, {@code resInsurance})은
 * {@link com.ntropy.account.client.codef.parser.AccountResponseParser}가 순회하지 않아 자동으로 건너뛴다.
 */
public enum AccountGroup {

    DEPOSIT_TRUST("resDepositTrust"),
    FOREIGN_CURRENCY("resForeignCurrency"),
    LOAN("resLoan");

    private final String responseFieldName;

    AccountGroup(String responseFieldName) {
        this.responseFieldName = responseFieldName;
    }

    public String getResponseFieldName() {
        return responseFieldName;
    }
}
