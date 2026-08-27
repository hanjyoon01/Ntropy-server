package com.ntropy.account.port.user;

/** 시딩된 가상회원 한 명의 식별자. userId는 실제 USERS 테이블의 AUTO_INCREMENT 값이다. */
public record SeededVirtualUser(
        Long userId,
        int ordinal
) {
}
