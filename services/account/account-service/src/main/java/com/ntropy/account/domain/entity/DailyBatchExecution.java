package com.ntropy.account.domain.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.ntropy.account.domain.BatchExecutionStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 일일 배치(provider별 증분 동기화 등)의 실행 상태와 DB 기반 분산 lease를 관리하는 도메인 객체.
 * {@code owner_id + lease_token}이 현재 소유자를 가리키며, heartbeat·완료·watermark 갱신은
 * 이 둘과 {@code status='RUNNING'}, {@code lease_until} 유효성을 모두 확인하는 fencing으로 보호한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class DailyBatchExecution {

    private Long id;
    private String jobName;
    private LocalDate businessDate;
    private BatchExecutionStatus status;
    private String ownerId;
    private String leaseToken;
    private LocalDateTime leaseUntil;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;

    /** 실패 기관/오류코드 목록 JSON 문자열. connectedId·계좌번호·생년월일 등 민감정보를 포함하지 않는다. */
    private String errorSummary;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
