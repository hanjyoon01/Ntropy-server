-- defense-service 초기 기능: 방어모드 진입/조회/수동 해제
CREATE TABLE IF NOT EXISTS DEFENSE_MODE
(
    defense_id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id               BIGINT      NOT NULL COMMENT 'user-service USER 참조 (크로스 도메인 FK 없음)',
    cause_code            VARCHAR(30) NOT NULL COMMENT 'ACCIDENT_INJURY, ILLNESS, PHYSICAL_RECOVERY, PLATFORM_RESTRICTION, FAMILY_CARE_CRISIS, OTHER',
    unavailable_start_date DATE       NOT NULL COMMENT '근무불가 시작일',
    expected_return_date  DATE        NOT NULL COMMENT '예상 복귀일',
    return_date           DATE        NULL COMMENT '사용자가 선언한 실제 복귀일',
    reserve_amount_snapshot BIGINT    NULL COMMENT '진입 시점 리저브 금액',
    safe_asset_amount_snapshot BIGINT NULL COMMENT '진입 시점 안전자산 금액',
    available_assets_snapshot BIGINT  NULL COMMENT '진입 시점 최근 재무진단의 유동자산',
    average_monthly_expense BIGINT     NULL COMMENT '최근 최대 3개월 총지출 평균',
    daily_expense         BIGINT      NULL COMMENT '월평균 지출을 30일로 나눈 올림값',
    d_day                 INT         NULL COMMENT '유동자산으로 버틸 수 있는 예상 일수',
    calculation_status    VARCHAR(30) NULL COMMENT 'CALCULATED, DIAGNOSIS_REQUIRED, EXPENSE_DATA_REQUIRED (예약 상태는 NULL)',
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'SCHEDULED, ACTIVE, RELEASED',
    created_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_defense_mode_user_status (user_id, status),
    INDEX idx_defense_mode_user_period (user_id, unavailable_start_date, expected_return_date)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4;

-- MySQL은 부분 유니크 인덱스를 지원하지 않으므로 사용자별 ACTIVE/SCHEDULED 중복은 서비스 트랜잭션에서 차단한다.

-- 기존 DEFENSE_MODE 테이블에는 위 스냅샷/계산 컬럼이 자동 추가되지 않는다.
-- 이미 생성된 로컬 DB는 해당 컬럼을 ALTER TABLE로 추가한 뒤 사용한다.
