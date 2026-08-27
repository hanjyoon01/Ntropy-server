package com.ntropy.account.exception;

/**
 * heartbeat가 실패해 배치 lease를 잃었다는 신호 (이슈 #158).
 * 이 예외를 받은 호출자는 계좌·기관·사용자 단위 실패로 취급해 넘어가지 말고,
 * 남은 처리를 즉시 중단해야 한다.
 */
public class LeaseLostException extends RuntimeException {

    public LeaseLostException() {
        super("lease를 잃어 처리를 중단합니다");
    }
}
