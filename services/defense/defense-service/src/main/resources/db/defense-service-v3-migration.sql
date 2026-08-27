-- 기존 DEFENSE_MODE 테이블에 디자인 표시용 자산 구성 스냅샷을 추가할 때 1회 실행한다.
ALTER TABLE DEFENSE_MODE
    ADD COLUMN reserve_amount_snapshot BIGINT NULL AFTER return_date,
    ADD COLUMN safe_asset_amount_snapshot BIGINT NULL AFTER reserve_amount_snapshot;
