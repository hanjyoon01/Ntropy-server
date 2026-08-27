-- 이슈 #166: USERS(provider, provider_id)에 unique key를 추가한다.
-- 신규 환경은 user-service-schema.sql만 실행하면 된다.
--
-- 적용 전, 기존 로컬/스테이징 DB에 이미 (provider, provider_id) 중복 행이 있는지 먼저 확인한다.
-- 아래 조회 결과가 비어 있어야 ALTER가 성공한다. 중복이 있다면 어느 행을 남길지 정책을 정한 뒤
-- 수동으로 정리하고 이 파일을 실행한다 (자동 삭제하지 않음).
--
-- SELECT provider, provider_id, COUNT(*) AS cnt
-- FROM USERS
-- GROUP BY provider, provider_id
-- HAVING COUNT(*) > 1;

ALTER TABLE USERS
    ADD UNIQUE KEY uk_users_provider_provider_id (provider, provider_id);
