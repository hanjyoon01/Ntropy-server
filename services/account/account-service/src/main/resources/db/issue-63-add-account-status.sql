-- 이슈 #63: 기존 ACCOUNT 테이블에 계좌 비활성화 상태를 추가한다.
-- 신규 환경은 account-service-schema.sql만 실행하면 된다.
ALTER TABLE ACCOUNT
    ADD COLUMN status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
        COMMENT '계좌 상태: ACTIVE, INACTIVE' AFTER next_payment_date,
    ADD COLUMN deactivated_at DATETIME NULL
        COMMENT '계좌 비활성화 시각' AFTER status;

ALTER TABLE ACCOUNT
    DROP INDEX ix_account_user,
    ADD INDEX ix_account_user_status (user_id, status);
