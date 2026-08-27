-- 이슈 #84: 대출·예적금 원천정보 컬럼을 추가하고, 더 이상 생성하지 않는
-- FUND/INSURANCE 가상 계좌·거래를 정리한다.
-- 신규 환경은 account-service-schema.sql만 실행하면 된다.

ALTER TABLE ACCOUNT
    ADD COLUMN loan_contract_principal DECIMAL(18,2) NULL
        COMMENT '대출 거래내역 계좌 상세의 약정원금(resPrincipal)' AFTER balance,
    ADD COLUMN interest_rate DECIMAL(9,4) NULL
        COMMENT '적금·대출 거래내역 계좌 상세의 이율(resRate). 보유계좌 응답에는 없어 미적용·응답 누락 시 null. backfill 금지'
        AFTER loan_contract_principal,
    ADD COLUMN maturity_date DATE NULL
        COMMENT '예금·적금·대출 만기일(resAccountEndDate)' AFTER account_start_date,
    MODIFY COLUMN account_group VARCHAR(20) NOT NULL
        COMMENT 'DEPOSIT_TRUST, FOREIGN_CURRENCY, LOAN';

ALTER TABLE ACCOUNT_TRANSACTION
    ADD COLUMN loan_transaction_type_name VARCHAR(100) NULL
        COMMENT '대출 거래내역 반복부의 거래구분명(resTransTypeNm) 원문' AFTER transaction_category,
    ADD COLUMN loan_principal_amount DECIMAL(18,2) NULL
        COMMENT '대출 거래내역 반복부의 원금(resPrincipal)' AFTER in_amount,
    ADD COLUMN loan_interest_amount DECIMAL(18,2) NULL
        COMMENT '대출 거래내역 반복부의 이자(resInterest)' AFTER loan_principal_amount,
    ADD COLUMN updated_at DATETIME NOT NULL
        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at;

-- 펀드·보험은 전용 ACCOUNT 행을 더 이상 생성하지 않는다. 기존 데이터는 전량 NTROPY 가상데이터이므로
-- INACTIVE 전환 대신 삭제한다(자식 ACCOUNT_TRANSACTION부터 삭제해 FK 제약을 지킨다).
DELETE FROM ACCOUNT_TRANSACTION
WHERE account_id IN (
    SELECT account_id FROM ACCOUNT WHERE account_group IN ('FUND', 'INSURANCE')
);

DELETE FROM ACCOUNT WHERE account_group IN ('FUND', 'INSURANCE');
