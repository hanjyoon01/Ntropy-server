package com.ntropy.account.domain;

/**
 * user-service에 아직 생년월일 필드가 없어(server/CLAUDE.local.md 참고), 실 CODEF 등록 규칙과
 * 형태를 맞춰야 할 때 임시로 쓰는 테스트 사용자 생년월일 값이다.
 * 가상(NTROPY) 계좌 생성 흐름은 이 값을 검증 게이트로 사용하지 않으며, 저장하거나 로그에 남기지 않는다.
 */
public final class TestUserFixture {

    public static final String BIRTH_DATE = "19900101";

    private TestUserFixture() {
    }
}
