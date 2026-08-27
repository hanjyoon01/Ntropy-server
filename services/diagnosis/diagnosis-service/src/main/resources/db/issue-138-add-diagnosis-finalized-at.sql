-- 이슈 #138: 기존 DIAGNOSIS_RESULT에 월말 확정 시각을 추가한다.
-- 신규 환경은 diagnosis-service-schema.sql만 실행하면 된다.

ALTER TABLE `DIAGNOSIS_RESULT`
    ADD COLUMN `finalized_at` DATETIME NULL
        COMMENT '월말 기준으로 확정된 시각. 진행 중인 현재 월은 NULL'
        AFTER `calculated_at`;
