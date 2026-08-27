package com.ntropy.account.domain;

/** {@code ACCOUNT_SYNC_STATE.last_status} 값. */
public enum AccountSyncStatus {
    PENDING,
    SUCCESS,
    PARTIAL_FAILED,
    SKIPPED_CREDENTIAL_REQUIRED
}
