package com.ntropy.account.config;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.Getter;

/**
 * DAILY_BATCH_EXECUTION lease 유지 시간 정책 (이슈 #158).
 * 별도 heartbeat 스레드를 두지 않고 사용자·기관 처리 루프마다 heartbeat로 lease_until을 연장하는
 * 것을 전제로 하므로, CODEF 최대 read timeout(로컬 DEMO 기준 310초)이 인증 오류로 한 번
 * 재시도되는 최악 시간과 연결·처리 여유를 합한 값보다 길게 잡는다. 기본 720초는 약 620초의
 * 두 번 read timeout 뒤에도 heartbeat를 갱신할 여유를 둔 값이다.
 */
@Getter
@Component
public class DailySyncLeaseProperties {

    private final Duration leaseDuration;

    public DailySyncLeaseProperties(
            @Value("${daily-sync.lease.duration-seconds:720}") long leaseDurationSeconds
    ) {
        if (leaseDurationSeconds <= 0) {
            throw new IllegalStateException("daily-sync.lease.duration-seconds는 양수여야 합니다");
        }
        this.leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    }
}
