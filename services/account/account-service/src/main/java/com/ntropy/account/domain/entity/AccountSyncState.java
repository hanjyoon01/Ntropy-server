package com.ntropy.account.domain.entity;

import java.time.LocalDateTime;

import com.ntropy.account.domain.AccountSyncStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 연결·기관별 증분 동기화 기준점(watermark)을 저장하는 도메인 객체.
 * {@code lastSuccessfulSyncedAt} 갱신은 {@link com.ntropy.account.service.BatchExecutionLeaseService}가
 * 부여한 lease를 가진 실행에서만 이뤄져야 한다 (fencing).
 */
@Getter
@Setter
@NoArgsConstructor
public class AccountSyncState {

    private Long id;
    private Long codefConnectionId;
    private String organizationCode;
    private LocalDateTime lastSuccessfulSyncedAt;
    private AccountSyncStatus lastStatus;
    private String lastErrorCode;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
