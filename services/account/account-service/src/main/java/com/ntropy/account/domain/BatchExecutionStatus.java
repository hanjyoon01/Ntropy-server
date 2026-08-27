package com.ntropy.account.domain;

/** {@code DAILY_BATCH_EXECUTION.status} 값. */
public enum BatchExecutionStatus {
    RUNNING,
    SUCCESS,
    PARTIAL_FAILED,
    FAILED
}
