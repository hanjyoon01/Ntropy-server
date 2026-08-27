package com.ntropy.account.domain;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Optional;

/**
 * CODEF 웹 기반 개인 ID 로그인으로 연결할 수 있는 은행 목록.
 * 대구은행(0031)은 출금계좌번호와 출금계좌 비밀번호가 추가로 필요해 지원 대상에서 제외한다.
 */
public enum PersonalBank {

    IBK_INDUSTRIAL_BANK("0003", "기업은행", true, 5),
    KB_KOOKMIN_BANK("0004", "국민은행", true, 5),
    NH_BANK("0011", "농협은행", false, 5),
    SC_BANK("0023", "SC은행", false, null),
    JEONBUK_BANK("0037", "전북은행", false, 5),
    KYONGNAM_BANK("0039", "경남은행", false, 3),
    SAEMAUL_GEUMGO("0045", "새마을금고", false, 5),
    KEB_HANA_BANK("0081", "KEB하나은행", false, 5),
    SHINHAN_BANK("0088", "신한은행", false, 5);

    private final String organizationCode;
    private final String displayName;
    private final boolean birthDateRequired;
    private final Integer passwordErrorLimit;

    PersonalBank(String organizationCode, String displayName,
                 boolean birthDateRequired, Integer passwordErrorLimit) {
        this.organizationCode = organizationCode;
        this.displayName = displayName;
        this.birthDateRequired = birthDateRequired;
        this.passwordErrorLimit = passwordErrorLimit;
    }

    public String getOrganizationCode() {
        return organizationCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isBirthDateRequired() {
        return birthDateRequired;
    }

    /**
     * SC은행처럼 ID/비밀번호 오류를 구분하지 않는 기관은 빈 값이다.
     */
    public Optional<Integer> getPasswordErrorLimit() {
        return Optional.ofNullable(passwordErrorLimit);
    }

    /**
     * 생년월일이 필요 없는 은행이면 {@code null}을 반환하고, 필요한 은행이면 YYYYMMDD 형식을
     * 검증한 뒤 그대로 반환한다. 보유계좌 등록과 거래내역 조회 양쪽에서 공용으로 쓴다.
     */
    public String normalizeBirthDate(String birthDate) {
        if (!birthDateRequired) {
            return null;
        }
        if (birthDate == null || birthDate.isBlank()) {
            throw new IllegalArgumentException(displayName + " 생년월일(YYYYMMDD)가 필요합니다");
        }
        if (!birthDate.matches("\\d{8}")) {
            throw new IllegalArgumentException("생년월일은 YYYYMMDD 형식이어야 합니다");
        }
        try {
            LocalDate.parse(birthDate, DateTimeFormatter.BASIC_ISO_DATE);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("유효한 생년월일을 입력해야 합니다", e);
        }
        return birthDate;
    }

    public static PersonalBank fromOrganizationCode(String organizationCode) {
        return Arrays.stream(values())
                .filter(bank -> bank.organizationCode.equals(organizationCode))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "지원하지 않는 은행 기관코드입니다: " + organizationCode
                ));
    }
}
