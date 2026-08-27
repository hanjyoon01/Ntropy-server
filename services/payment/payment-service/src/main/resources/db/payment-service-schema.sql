DROP TABLE IF EXISTS `PAYMENT`;
DROP TABLE IF EXISTS `SUBSCRIPTION`;

-- 1. SUBSCRIPTION (구독 상태 / 결제수단)
CREATE TABLE `SUBSCRIPTION` (
                                `subscription_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
                                `user_id`	BIGINT	NOT NULL,
                                `plan_code`	VARCHAR(20)	NULL,
                                `start_date`	DATETIME	NULL,
                                `end_date`	DATETIME	NULL,
                                `cancel_requested_at`	DATETIME	NULL,
                                `auto_renew_yn`	BOOLEAN	NULL,
                                `customer_uid`	VARCHAR(100)	NULL,
                                `payment_method`	VARCHAR(20)	NULL	COMMENT 'CARD | KAKAOPAY | TOSSPAY',
                                `payment_label`	VARCHAR(50)	NULL	COMMENT '화면 표시용 (카드=카드사명, 간편결제=카카오페이/토스페이)',
                                `payment_masked`	VARCHAR(30)	NULL	COMMENT 'CARD만 값 있음. 간편결제는 카드정보가 안 넘어와서 항상 NULL',
                                `status`	VARCHAR(20)	NULL,
                                PRIMARY KEY (`subscription_id`)
);

-- 2. PAYMENT (결제 내역)
CREATE TABLE `PAYMENT` (
                           `payment_id`	BIGINT	NOT NULL	AUTO_INCREMENT,
                           `subscription_id`	BIGINT	NOT NULL,
                           `plan_code`	VARCHAR(20)	NULL,
                           `imp_uid`	VARCHAR(50)	NULL,
                           `amount`	BIGINT	NULL,
                           `payment_method`	VARCHAR(20)	NULL,
                           `created_at`	DATETIME	NULL,
                           `merchant_uid`	VARCHAR(100)	NULL	COMMENT 'UNIQUE',
                           `payment_status`	VARCHAR(20)	NULL,
                           `receipt_url`	VARCHAR(500)	NULL,
                           `failure_reason`	VARCHAR(255)	NULL,
                           PRIMARY KEY (`payment_id`)
);

-- ============================================================
-- Foreign Key (payment-service 내부 참조만 - 서비스 내부는 FK 유지)
-- ============================================================

ALTER TABLE `PAYMENT` ADD CONSTRAINT `FK_SUBSCRIPTION_TO_PAYMENT_1` FOREIGN KEY (
                                                                                 `subscription_id`
    )
    REFERENCES `SUBSCRIPTION` (
                               `subscription_id`
        ) ON DELETE CASCADE;

-- ============================================================
-- Unique Constraint
-- ============================================================

ALTER TABLE `PAYMENT` ADD CONSTRAINT `UQ_PAYMENT_MERCHANT_UID` UNIQUE (`merchant_uid`);

ALTER TABLE `SUBSCRIPTION` ADD CONSTRAINT `UQ_SUBSCRIPTION_CUSTOMER_UID` UNIQUE (`customer_uid`);

-- ============================================================
-- Index
-- ============================================================

CREATE INDEX `IDX_PAYMENT_SUBSCRIPTION_ID` ON `PAYMENT` (`subscription_id`);

CREATE INDEX `IDX_SUBSCRIPTION_USER_ID` ON `SUBSCRIPTION` (`user_id`);

-- ============================================================
-- 참고: 아래 컬럼은 문자열(VARCHAR)로 저장되지만 애플리케이션에서는 enum으로 강제한다.
--   SUBSCRIPTION.plan_code       : BASIC | PRO
--   SUBSCRIPTION.status          : ACTIVE | CANCEL_SCHEDULED | EXPIRED | PAYMENT_FAILED
--   SUBSCRIPTION.payment_method  : CARD | KAKAOPAY | TOSSPAY (결제수단별 포트원 채널 분리 확정)
--   PAYMENT.payment_method       : CARD | KAKAOPAY | TOSSPAY (위와 동일 값 범위)
--   PAYMENT.payment_status       : SUCCESS | FAILED | RETRY | CANCELLED
-- ============================================================