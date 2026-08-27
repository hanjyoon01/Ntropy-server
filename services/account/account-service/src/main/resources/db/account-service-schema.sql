-- ============================================================
-- account-service 소유 금융 테이블 최종 DDL
-- 대상: CODEF_CONNECTION, CODEF_TOKEN, ACCOUNT, ACCOUNT_TRANSACTION
--
-- 서비스 내부 참조에는 FK를 사용한다.
-- user_id는 다른 서비스 소유 키이므로 FK 없이 논리 참조와 인덱스만 둔다.
-- FIN-004, FIN-005, FIN-007, FIN-008의 누적 결과가 반영되어 있으므로
-- 신규 환경은 이 파일만 실행한다.
-- ============================================================

CREATE TABLE IF NOT EXISTS CODEF_CONNECTION
(
    codef_connection_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id                     BIGINT       NOT NULL COMMENT 'user-service USER.id 참조 (크로스 도메인 FK 없음)',
    provider                    VARCHAR(10)  NOT NULL DEFAULT 'CODEF' COMMENT '연결 제공자: CODEF(실제 CODEF 연동), NTROPY(가상 연결, 이슈 #35)',
    connected_id                VARCHAR(100) NOT NULL COMMENT 'CODEF 커넥티드 아이디 (실연결은 계정 등록 API 응답값, 가상연결은 서버에서 발급한 NTROPY-{UUID})',
    registered_institution_keys JSON         NULL COMMENT '등록 완료 기관코드 JSON 배열, 예: ["0004","0088"]. 동일 기관 중복 /account/add 요청 방지용',
    birth_date_ciphertext       VARCHAR(255) NULL COMMENT '기업·국민은행 birthDate AES-256-GCM 암호문(Base64). 이슈 #158',
    birth_date_iv               VARCHAR(32)  NULL COMMENT '암호화마다 새로 생성하는 12바이트 IV(Base64). 이슈 #158',
    birth_date_key_version      BIGINT       NULL COMMENT '암호화 당시 사용한 키 버전. 키 회전 시 복호화에 필요. 이슈 #158',
    created_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at                  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_codef_connection_user_provider (user_id, provider)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- CODEF OAuth2 accessToken 캐시. client_credentials 방식이라 사용자 단위가 아닌 클라이언트(서비스) 단위로 존재.
-- 지금은 DB로만 캐싱하고, 추후 Redis 도입 시 CodefTokenStore의 Redis 구현체로 대체 예정 (DEVLOG 참고).
CREATE TABLE IF NOT EXISTS CODEF_TOKEN
(
    codef_token_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    service_type VARCHAR(20)   NOT NULL COMMENT 'SANDBOX, DEMO, API',
    client_id    VARCHAR(100)  NOT NULL COMMENT '토큰을 발급받은 CODEF OAuth 클라이언트 식별자',
    access_token VARCHAR(2000) NOT NULL,
    expires_at   DATETIME      NOT NULL COMMENT 'accessToken 만료 시각 (발급 시각 + expires_in)',
    created_at   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX ix_codef_token_lookup (service_type, client_id, codef_token_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- CODEF 보유계좌 조회(account-list) 응답을 저장. 예금/신탁·외화·대출 3개 그룹을 account_group으로 구분하는 단일 테이블.
-- 펀드·보험 영역은 전용 행을 만들지 않는다(보험료는 입출금계좌 거래내역으로 분석).
-- 계좌번호 원문은 저장하지 않고 표시용 마스킹 값과 중복 판별용 해시만 저장한다.
CREATE TABLE IF NOT EXISTS ACCOUNT
(
    account_id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    codef_connection_id BIGINT        NOT NULL COMMENT 'CODEF_CONNECTION.codef_connection_id 참조',
    user_id             BIGINT        NOT NULL COMMENT 'user-service USER.id 참조 (크로스 도메인 FK 없음), 조회 편의를 위한 비정규화',
    organization_code   VARCHAR(10)   NOT NULL COMMENT 'CODEF 기관코드',
    account_group       VARCHAR(20)   NOT NULL COMMENT 'DEPOSIT_TRUST, FOREIGN_CURRENCY, LOAN',
    deposit_type_code   VARCHAR(2)    NOT NULL COMMENT 'resAccountDeposit 분류값 (10~99)',
    account_no_masked   VARCHAR(64)   NOT NULL COMMENT '서버 생성 표시용 마스킹 계좌번호 (끝 4자리만 노출)',
    account_no_hash     CHAR(64)      NOT NULL COMMENT 'SHA-256(기관코드+실제계좌번호) 중복 판별용 해시. 원문 계좌번호는 저장하지 않음',
    account_name        VARCHAR(100)  NULL COMMENT 'resAccountName 우선, 없으면 resAccountNickName',
    balance             DECIMAL(18,2) NULL COMMENT '자산 잔액 또는 정규화된 부채 잔액(양수)',
    loan_contract_principal DECIMAL(18,2) NULL COMMENT '대출 거래내역 계좌 상세의 약정원금(resPrincipal)',
    interest_rate       DECIMAL(9,4)  NULL COMMENT '적금·대출 거래내역 계좌 상세의 이율(resRate). 보유계좌 응답에는 없어 미적용·응답 누락 시 null. backfill 금지',
    currency_code       VARCHAR(3)    NOT NULL DEFAULT 'KRW' COMMENT 'resAccountCurrency (ISO 4217)',
    account_start_date  DATE          NULL COMMENT '정규화된 상품 시작일(마이너스통장은 resLoanStartDate 우선)',
    maturity_date       DATE          NULL COMMENT '예금·적금·대출 만기일(resAccountEndDate)',
    last_tran_date      DATE          NULL COMMENT 'resLastTranDate',
    overdraft_yn        BOOLEAN       NULL COMMENT 'resOverdraftAcctYN (예금/신탁)',
    next_payment_date   DATE          NULL COMMENT '다음 적금 납입일 또는 대출 상환일(resDatePayment/추정)',
    status              VARCHAR(10)   NOT NULL DEFAULT 'ACTIVE' COMMENT '계좌 상태: ACTIVE, INACTIVE',
    deactivated_at      DATETIME      NULL COMMENT '계좌 비활성화 시각',
    created_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_connection_hash (codef_connection_id, account_no_hash),
    INDEX ix_account_user_status (user_id, status),
    CONSTRAINT fk_account_codef_connection FOREIGN KEY (codef_connection_id)
        REFERENCES CODEF_CONNECTION (codef_connection_id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- CODEF 수시입출 거래내역(transaction-list) 응답을 저장. fingerprint로 동일 거래의 반복 저장을 막는다.
CREATE TABLE IF NOT EXISTS ACCOUNT_TRANSACTION
(
    account_transaction_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_id    BIGINT        NOT NULL COMMENT 'ACCOUNT.account_id 참조',
    fingerprint   CHAR(64)      NOT NULL COMMENT '계좌·거래일시·상품별 금액·상세 기반 SHA-256',
    transaction_category VARCHAR(20) NOT NULL DEFAULT 'ORDINARY' COMMENT 'ORDINARY, INSTALLMENT, LOAN',
    loan_transaction_type_name VARCHAR(100) NULL COMMENT '대출 거래내역 반복부의 거래구분명(resTransTypeNm) 원문',
    tran_date     DATE          NULL COMMENT 'resAccountTrDate',
    tran_time     TIME          NULL COMMENT 'resAccountTrTime (hhmmss)',
    out_amount    DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'resAccountOut, 대출은 총상환액(resTranAmount 없으면 원금+이자)',
    in_amount     DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT 'resAccountIn',
    loan_principal_amount DECIMAL(18,2) NULL COMMENT '대출 거래내역 반복부의 원금(resPrincipal)',
    loan_interest_amount  DECIMAL(18,2) NULL COMMENT '대출 거래내역 반복부의 이자(resInterest)',
    after_balance DECIMAL(18,2) NULL COMMENT '거래 후 잔액 또는 대출 잔액',
    desc1         VARCHAR(255)  NULL COMMENT 'resAccountDesc1, 은행별 의미가 달라 원본 필드명 유지',
    desc2         VARCHAR(255)  NULL COMMENT 'resAccountDesc2, 은행별 의미가 달라 원본 필드명 유지',
    desc3         VARCHAR(255)  NULL COMMENT 'resAccountDesc3, 은행별 의미가 달라 원본 필드명 유지',
    desc4         VARCHAR(255)  NULL COMMENT 'resAccountDesc4, 은행별 의미가 달라 원본 필드명 유지',
    created_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_transaction_fingerprint (account_id, fingerprint),
    INDEX ix_account_transaction_account_date (account_id, tran_date),
    CONSTRAINT fk_account_transaction_account FOREIGN KEY (account_id) REFERENCES ACCOUNT (account_id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- 일일 배치(provider별 증분 동기화 등)의 실행을 job_name(예: daily-sync-codef, daily-sync-ntropy)
-- 단위로 관리한다. UNIQUE(job_name, business_date)로 동일 영업일 중복 실행을 막고,
-- owner_id + lease_token 기반 fencing으로 heartbeat·완료·watermark 갱신을 보호한다 (이슈 #158).
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

-- 연결·기관별 증분 동기화 기준점(watermark). last_successful_synced_at 갱신은 반드시
-- DAILY_BATCH_EXECUTION의 유효한 lease_token을 가진 실행에서만 이뤄져야 한다 (fencing, 이슈 #158).
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
        REFERENCES CODEF_CONNECTION (codef_connection_id) ON DELETE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

CREATE TABLE IF NOT EXISTS TXN_ANALYSIS
(
    txn_analysis_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    account_transaction_id BIGINT      NOT NULL,
    is_consumption         BOOLEAN     NOT NULL,
    category               VARCHAR(32) NULL,
    expense_type           VARCHAR(16) NULL,
    classified_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_txn_analysis_transaction (
                                               account_transaction_id
                                           ),

    CONSTRAINT fk_txn_analysis_transaction
    FOREIGN KEY (account_transaction_id)
    REFERENCES ACCOUNT_TRANSACTION (account_transaction_id) ON DELETE CASCADE
    ) ENGINE = InnoDB
    DEFAULT CHARSET = utf8mb4;