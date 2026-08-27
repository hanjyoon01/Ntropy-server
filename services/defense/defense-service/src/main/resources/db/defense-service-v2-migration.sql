-- 기존 DEFENSE_MODE 테이블에 재무진단 스냅샷 및 D-Day 컬럼을 추가할 때 1회 실행한다.
ALTER TABLE DEFENSE_MODE
    ADD COLUMN available_assets_snapshot BIGINT NULL AFTER return_date,
    ADD COLUMN average_monthly_expense BIGINT NULL AFTER available_assets_snapshot,
    ADD COLUMN daily_expense BIGINT NULL AFTER average_monthly_expense,
    ADD COLUMN d_day INT NULL AFTER daily_expense,
    ADD COLUMN calculation_status VARCHAR(30) NOT NULL DEFAULT 'DIAGNOSIS_REQUIRED' AFTER d_day;

