-- 이슈 #158: 일일 금융거래 증분 동기화를 위한 DB 기반 분산 lease와
-- 연결·기관별 동기화 기준점(watermark) 테이블, 기업·국민은행 birthDate 암호화 저장 컬럼을 추가한다.
-- 신규 환경은 account-service-schema.sql만 실행하면 된다.

ALTER TABLE CODEF_CONNECTION
    ADD COLUMN birth_date_ciphertext VARCHAR(255) NULL
        COMMENT '기업·국민은행 birthDate AES-256-GCM 암호문(Base64). 이슈 #158' AFTER registered_institution_keys,
    ADD COLUMN birth_date_iv VARCHAR(32) NULL
        COMMENT '암호화마다 새로 생성하는 12바이트 IV(Base64). 이슈 #158' AFTER birth_date_ciphertext,
    ADD COLUMN birth_date_key_version BIGINT NULL
        COMMENT '암호화 당시 사용한 키 버전. 키 회전 시 복호화에 필요. 이슈 #158' AFTER birth_date_iv;

CREATE TABLE IF NOT EXISTS DAILY_BATCH_EXECUTION
(
    daily_batch_execution_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name      VARCHAR(50)  NOT NULL COMMENT 'provider별 독립 job: daily-sync-codef, daily-sync-ntropy',
    business_date DATE         NOT NULL,
    status        VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING, SUCCESS, PARTIAL_FAILED, FAILED',
    owner_id      VARCHAR(100) NOT NULL COMMENT '실행 인스턴스 식별자 (예: host:port)',
    lease_token   VARCHAR(36)  NOT NULL COMMENT 'lease 획득마다 새로 발급하는 UUID. heartbeat·완료·watermark fencing에 사용',
    lease_until   DATETIME     NOT NULL COMMENT '이 시각 이전에는 다른 인스턴스가 인계할 수 없음',
    started_at    DATETIME     NOT NULL,
    completed_at  DATETIME     NULL,
    error_summary JSON         NULL COMMENT '실패 기관/오류코드 목록. connectedId·계좌번호·생년월일 등 민감정보 금지',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_daily_batch_execution_job_date (job_name, business_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS ACCOUNT_SYNC_STATE
(
    account_sync_state_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    codef_connection_id       BIGINT      NOT NULL COMMENT 'CODEF_CONNECTION.codef_connection_id 참조',
    organization_code         VARCHAR(10) NOT NULL COMMENT 'CODEF 기관코드',
    last_successful_synced_at DATETIME    NULL COMMENT '이 연결·기관의 마지막 증분 동기화 성공 시각',
    last_status                VARCHAR(30) NOT NULL DEFAULT 'PENDING'
        COMMENT 'PENDING, SUCCESS, PARTIAL_FAILED, SKIPPED_CREDENTIAL_REQUIRED',
    last_error_code             VARCHAR(50) NULL,
    created_at                  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_sync_state_connection_org (codef_connection_id, organization_code),
    CONSTRAINT fk_account_sync_state_codef_connection FOREIGN KEY (codef_connection_id)
        REFERENCES CODEF_CONNECTION (codef_connection_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;
