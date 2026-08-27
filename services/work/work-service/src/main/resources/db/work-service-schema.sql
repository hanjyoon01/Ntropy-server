-- ============================================================
-- work-service 소유 테이블 DDL
-- 소유 서비스: work-service
-- 대상: CATEGORY, PLATFORM, JOBPLATFORMMAPPING, JOB, JOB_SCHEDULE, WORK_LOG, ALLOCATION_GOAL
-- 생성 순서: 참조 관계상 부모 -> 자식 순
-- 주의: user_id는 user-service USER를 참조하지만 크로스 도메인 FK는 걸지 않음 (팀 규칙)
-- ============================================================

-- 1. CATEGORY (마스터 데이터)
CREATE TABLE `CATEGORY` (
	`category_id`	BIGINT	NOT NULL,
	`name`	VARCHAR(50)	NOT NULL
);

-- 2. PLATFORM (플랫폼 마스터 - 사용자 가입 이전에 미리 시딩되는 참조 데이터)
CREATE TABLE `PLATFORM` (
	`platform_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`platform_name`	VARCHAR(50)	NOT NULL	COMMENT '플랫폼명 (배달의민족/카카오T대리 등)',
	`deposit_name`	VARCHAR(100)	NOT NULL	COMMENT '입금 거래내역 대조용 입금처명',
	`settlement_cycle`	VARCHAR(20)	NOT NULL	COMMENT '정산주기 (DAILY/WEEKLY/MONTHLY)',
	`settlement_trigger_type`	VARCHAR(20)	NOT NULL	DEFAULT 'AUTO'	COMMENT '정산 트리거 방식: AUTO(플랫폼이 주기에 따라 자동 입금)/ON_DEMAND(포인트 적립 후 사용자가 출금 신청해야 계좌 입금 - 실거래 매칭 불가, 확정 시점에 즉시 정산완료 처리)',
	`settlement_offset_day`	INT	NULL	COMMENT '정산 기간 종료일로부터 입금일까지 며칠인지 (DAILY/WEEKLY 공용)',
	`settlement_offset_unit`	VARCHAR(20)	NOT NULL	DEFAULT 'CALENDAR_DAY'	COMMENT 'settlement_offset_day의 단위: CALENDAR_DAY(달력일)/BUSINESS_DAY(영업일). WEEKLY/MONTHLY 요일·일자 고정형은 미사용(기본값)',
	`settlement_day_of_week`	VARCHAR(20)	NULL	COMMENT 'WEEKLY 전용: MON~SUN',
	`settlement_day_of_month`	INT	NULL	COMMENT 'MONTHLY 전용: 1~31'
);

-- 3. JOBPLATFORMMAPPING (사용자별 잡-플랫폼 연결 인스턴스)
CREATE TABLE `JOBPLATFORMMAPPING` (
	`mapping_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`job_id`	BIGINT	NOT NULL,
	`platform_id`	BIGINT	NOT NULL,
	PRIMARY KEY (`mapping_id`)
);

-- 4. JOB (잡 등록)
CREATE TABLE `JOB` (
	`job_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL,
	`category_id`	BIGINT	NOT NULL,
	`job_name`	VARCHAR(50)	NOT NULL	COMMENT '사용자가 직접 입력하는 잡 라벨 (카테고리만으로 구분 안 되는 실제 근무지명)',
	`settlement_type`	VARCHAR(20)	NOT NULL,
	`hourly_wage`	INT	NULL,
	`monthly_wage`	INT	NULL,
	`per_task_wage`	INT	NULL,
	`task_per_hour`	FLOAT	NULL,
	`is_regular`	BOOLEAN	NOT NULL,
	`base_fatigue`	INT	NOT NULL,
	`monthly_expected_income`	BIGINT	NULL	COMMENT '월 환산 예상 소득 (등록/수정 시 정산 방식에 따라 서버가 계산해 저장. PER_TASK는 계산 방식 미정이라 NULL)',
	`created_at`	DATETIME	NOT NULL,
	`updated_at`	DATETIME	NOT NULL,
	`is_active`	BOOLEAN	NOT NULL,
	PRIMARY KEY (`job_id`)
);

-- 5. JOB_SCHEDULE (정기 근무 스케줄)
CREATE TABLE `JOB_SCHEDULE` (
	`schedule_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`job_id`	BIGINT	NOT NULL,
	`day_of_week`	VARCHAR(10)	NULL,
	`start_time`	TIME	NULL,
	`end_time`	TIME	NULL,
	PRIMARY KEY (`schedule_id`)
);

-- 6. WORK_LOG (근무 계획/실적 기록)
CREATE TABLE `WORK_LOG` (
	`log_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL,
	`job_id`	BIGINT	NOT NULL,
	`work_date`	DATE	NULL,
	`start_time`	TIME	NULL,
	`end_time`	TIME	NULL,
	`task_count`	BIGINT	NULL	COMMENT 'PER_TASK 잡 확정 시 사용자가 입력하는 실제 건수',
	`fatigue`	BIGINT	NOT NULL,
	`estimated_income`	BIGINT	NULL,
	`status`	VARCHAR(20)	NULL	COMMENT 'PLANNED/CONFIRMED',
	`settlement_status`	VARCHAR(20)	NULL	COMMENT 'NONE/PENDING/PARTIAL/COMPLETED - WORK_LOG_PLATFORM_INCOME 행들 상태로부터 파생 계산됨',
	PRIMARY KEY (`log_id`)
);

-- 7. ALLOCATION_GOAL (잡별 근무시간 배분 추천)
CREATE TABLE `ALLOCATION_GOAL` (
	`allocation_goal_id`	BIGINT	NOT NULL AUTO_INCREMENT,
	`job_id`	BIGINT	NOT NULL,
	`target_month`	VARCHAR(7)	NOT NULL,
	`recommend_hour`	BIGINT	NOT NULL,
	PRIMARY KEY (`allocation_goal_id`)
);

-- 8. SAVING_GOAL (월별 저축 목표: 목표 소득액 + 적정 피로도)
CREATE TABLE `SAVING_GOAL` (
	`saving_goal_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL,
	`target_month`	VARCHAR(7)	NOT NULL	COMMENT 'YYYY-MM',
	`target_amount`	BIGINT	NOT NULL,
	`labor_intensity`	BIGINT	NOT NULL	COMMENT '적정 피로도 T, 1~5',
	PRIMARY KEY (`saving_goal_id`)
);

-- 9. HOLIDAY (특일 정보 API로 조회한 공휴일 캐시 - 연도 단위로 채워짐)
CREATE TABLE `HOLIDAY` (
	`holiday_date`	DATE	NOT NULL,
	`name`	VARCHAR(50)	NOT NULL	COMMENT '공휴일명 (예: 신정, 설날)',
	PRIMARY KEY (`holiday_date`)
);

-- 10. WORK_LOG_PLATFORM_INCOME (근무일지 1건의 소득을 플랫폼별로 분배 - 여러 플랫폼을 동시에
--     운영하는 배달 등에서, 근무일지는 안 쪼개고 소득/정산 상태만 플랫폼별로 추적하기 위함)
CREATE TABLE `WORK_LOG_PLATFORM_INCOME` (
	`income_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`log_id`	BIGINT	NOT NULL,
	`platform_id`	BIGINT	NOT NULL,
	`expected_amount`	BIGINT	NOT NULL,
	`settlement_status`	VARCHAR(20)	NOT NULL	COMMENT 'PENDING/COMPLETED (WORK_LOG와 달리 NONE/PARTIAL은 없음 - 확정 시점에만 생성되므로)',
	PRIMARY KEY (`income_id`)
);

-- 11. SETTLEMENT (입금 거래-플랫폼/잡 매칭 결과. 매칭 성공(MATCHED)/실패(UNMATCHED) 모두 기록)
--     account_transaction_id는 account-service ACCOUNT_TRANSACTION 참조지만 크로스 도메인이라
--     FK는 걸지 않는다(팀 규칙). SettlementService.saveUnmatchedSettlement 참고.
CREATE TABLE `SETTLEMENT` (
	`settlement_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
	`user_id`	BIGINT	NOT NULL,
	`status`	VARCHAR(20)	NOT NULL	COMMENT 'MATCHED/UNMATCHED',
	`job_id`	BIGINT	NULL	COMMENT 'UNMATCHED는 잡을 특정할 수 없어 NULL',
	`period_start`	DATE	NOT NULL,
	`period_end`	DATE	NOT NULL,
	`deposit_date`	DATE	NOT NULL	COMMENT '실제 입금일 (ACCOUNT_TRANSACTION.tran_date)',
	`expected_amount`	BIGINT	NOT NULL	COMMENT '이 거래가 새로 정산 완료한 WORK_LOG_PLATFORM_INCOME 예상액 합계 스냅샷 (동일 기간 top-up은 신규 PENDING분만, UNMATCHED는 0)',
	`actual_amount`	BIGINT	NOT NULL	COMMENT '매칭된 실제 입금액 합계',
	`transaction_count`	INT	NOT NULL	COMMENT '이 SETTLEMENT 행에 합산된 거래 건수',
	`account_transaction_id`	BIGINT	NULL	COMMENT 'account-service ACCOUNT_TRANSACTION 참조 (크로스 도메인 FK 없음). UNMATCHED는 NULL',
	`matched_at`	DATETIME	NOT NULL,
	PRIMARY KEY (`settlement_id`)
);

-- ============================================================
-- Primary Key
-- ============================================================

ALTER TABLE `CATEGORY` ADD CONSTRAINT `PK_CATEGORY` PRIMARY KEY (
	`category_id`
);

ALTER TABLE `PLATFORM` ADD CONSTRAINT `PK_PLATFORM` PRIMARY KEY (
	`platform_id`
);

-- ============================================================
-- Foreign Key (work-service 내부 참조만 - 서비스 내부는 FK 유지)
-- ============================================================

ALTER TABLE `JOB` ADD CONSTRAINT `FK_CATEGORY_TO_JOB_1` FOREIGN KEY (
	`category_id`
)
REFERENCES `CATEGORY` (
	`category_id`
);

ALTER TABLE `PLATFORM` ADD CONSTRAINT `FK_CATEGORY_TO_PLATFORM_1` FOREIGN KEY (
	`category_id`
)
REFERENCES `CATEGORY` (
	`category_id`
);

ALTER TABLE `JOBPLATFORMMAPPING` ADD CONSTRAINT `FK_JOB_TO_JOBPLATFORMMAPPING_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
) ON DELETE CASCADE;

ALTER TABLE `JOBPLATFORMMAPPING` ADD CONSTRAINT `FK_PLATFORM_TO_JOBPLATFORMMAPPING_1` FOREIGN KEY (
	`platform_id`
)
REFERENCES `PLATFORM` (
	`platform_id`
);

ALTER TABLE `JOB_SCHEDULE` ADD CONSTRAINT `FK_JOB_TO_JOB_SCHEDULE_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
) ON DELETE CASCADE;

ALTER TABLE `WORK_LOG` ADD CONSTRAINT `FK_JOB_TO_WORK_LOG_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
) ON DELETE CASCADE;

ALTER TABLE `WORK_LOG_PLATFORM_INCOME` ADD CONSTRAINT `FK_WORK_LOG_TO_WORK_LOG_PLATFORM_INCOME_1` FOREIGN KEY (
	`log_id`
)
REFERENCES `WORK_LOG` (
	`log_id`
) ON DELETE CASCADE;

ALTER TABLE `WORK_LOG_PLATFORM_INCOME` ADD CONSTRAINT `FK_PLATFORM_TO_WORK_LOG_PLATFORM_INCOME_1` FOREIGN KEY (
	`platform_id`
)
REFERENCES `PLATFORM` (
	`platform_id`
);

ALTER TABLE `SETTLEMENT` ADD CONSTRAINT `FK_JOB_TO_SETTLEMENT_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
) ON DELETE CASCADE;

ALTER TABLE `ALLOCATION_GOAL` ADD CONSTRAINT `FK_JOB_TO_ALLOCATION_GOAL_1` FOREIGN KEY (
	`job_id`
)
REFERENCES `JOB` (
	`job_id`
) ON DELETE CASCADE;

-- ============================================================
-- Index (job_id 기준 - 체크리스트 항목)
-- ============================================================

CREATE INDEX `IDX_JOBPLATFORMMAPPING_JOB_ID` ON `JOBPLATFORMMAPPING` (`job_id`);
CREATE INDEX `IDX_JOBPLATFORMMAPPING_MASTER_ID` ON `JOBPLATFORMMAPPING` (`platform_id`);
CREATE INDEX `IDX_PLATFORM_CATEGORY_ID` ON `PLATFORM` (`category_id`);
CREATE INDEX `IDX_JOB_SCHEDULE_JOB_ID` ON `JOB_SCHEDULE` (`job_id`);
CREATE INDEX `IDX_WORK_LOG_JOB_ID` ON `WORK_LOG` (`job_id`);
CREATE INDEX `IDX_ALLOCATION_GOAL_JOB_ID` ON `ALLOCATION_GOAL` (`job_id`);
CREATE INDEX `IDX_WORK_LOG_PLATFORM_INCOME_LOG_ID` ON `WORK_LOG_PLATFORM_INCOME` (`log_id`);
CREATE INDEX `IDX_WORK_LOG_PLATFORM_INCOME_PLATFORM_ID` ON `WORK_LOG_PLATFORM_INCOME` (`platform_id`);
CREATE INDEX `IDX_SETTLEMENT_ACCOUNT_TRANSACTION_ID` ON `SETTLEMENT` (`account_transaction_id`);
CREATE INDEX `IDX_SETTLEMENT_USER_STATUS_PERIOD` ON `SETTLEMENT` (`user_id`, `status`, `period_start`, `period_end`);
CREATE INDEX `IDX_SETTLEMENT_USER_DEPOSIT_DATE` ON `SETTLEMENT` (`user_id`, `deposit_date`);

-- user_id는 크로스 도메인(user-service)이라 FK는 걸지 않되, 조회 성능을 위해 인덱스는 추가
CREATE INDEX `IDX_JOB_USER_ID` ON `JOB` (`user_id`);
CREATE INDEX `IDX_WORK_LOG_USER_ID` ON `WORK_LOG` (`user_id`);
CREATE INDEX `IDX_SAVING_GOAL_USER_ID` ON `SAVING_GOAL` (`user_id`);

-- 캘린더 월별 조회 시 자주 쓰이는 조합이라 함께 추가
CREATE INDEX `IDX_WORK_LOG_USER_DATE` ON `WORK_LOG` (`user_id`, `work_date`);


CREATE UNIQUE INDEX `UK_ALLOCATION_GOAL_JOB_MONTH`
    ON `ALLOCATION_GOAL` (`job_id`, `target_month`);
