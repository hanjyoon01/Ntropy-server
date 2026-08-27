-- ============================================================
-- work-service 목데이터 (개발/시연용)
-- 대상: JOB, JOBPLATFORMMAPPING, JOB_SCHEDULE, WORK_LOG, ALLOCATION_GOAL, SAVING_GOAL
-- 전제: work-service-seed.sql(CATEGORY, PLATFORM)이 먼저 반영되어 있어야 함
-- user_id는 크로스 도메인이라 임의값(1) 사용 - 실제 계정과 맞출 필요 있으면 값만 교체
-- ============================================================

-- ------------------------------------------------------------
-- JOB
--   101: 배달 (배민/쿠팡이츠 겸업, 비정규, 시급)
--   103: 유튜브 (콘텐츠 제작, 부업, 건당)
-- ------------------------------------------------------------
INSERT INTO `JOB`
    (job_id, user_id, category_id, job_name, settlement_type,
     hourly_wage, monthly_wage, per_task_wage, task_per_hour,
     is_regular, base_fatigue, monthly_expected_income,
     created_at, updated_at, is_active)
VALUES
(101, 1, 1, '배달의민족 라이더', 'HOURLY', 12000, NULL, NULL, NULL,
 FALSE, 3, 960000, '2026-07-01 09:00:00', '2026-07-01 09:00:00', TRUE),
(103, 1, 6, '유튜브 브이로그', 'PER_TASK', NULL, NULL, 50000, 0.5,
 FALSE, 4, NULL, '2026-07-01 09:00:00', '2026-07-01 09:00:00', TRUE);

-- ------------------------------------------------------------
-- JOBPLATFORMMAPPING (job - platform 연결)
-- ------------------------------------------------------------
INSERT INTO `JOBPLATFORMMAPPING` (job_id, platform_id) VALUES
(101, 1),   -- 배달의민족 라이더 -> 배달의민족
(101, 2),   -- 배달의민족 라이더 -> 쿠팡이츠 겸업
(103, 5);   -- 유튜브 브이로그 -> 유튜브

-- ------------------------------------------------------------
-- WORK_LOG (최근 2주치 근무 기록/계획)
-- ------------------------------------------------------------
INSERT INTO `WORK_LOG`
    (user_id, job_id, work_date, start_time, end_time,
     task_count, fatigue, estimated_income, status, settlement_status)
VALUES
-- 배달 (건별 근무, 확정)
(1, 101, '2026-07-28', '18:00:00', '22:00:00', NULL, 3, 48000, 'CONFIRMED', 'COMPLETED'),
(1, 101, '2026-07-30', '18:00:00', '23:00:00', NULL, 4, 60000, 'CONFIRMED', 'COMPLETED'),
(1, 101, '2026-08-02', '18:00:00', '22:00:00', NULL, 3, 48000, 'CONFIRMED', 'PENDING'),
(1, 101, '2026-08-05', '18:00:00', '22:00:00', NULL, 3, 48000, 'PLANNED', 'NONE'),

-- 유튜브 (건당, task_count 사용)
(1, 103, '2026-07-27', NULL, NULL, 2, 4, 100000, 'CONFIRMED', 'COMPLETED'),
(1, 103, '2026-08-03', NULL, NULL, 1, 4, 50000, 'PLANNED', 'NONE');

-- ------------------------------------------------------------
-- ALLOCATION_GOAL (8월 근무시간 배분 추천 - 배달/유튜브만, 정규직 제외)
-- ------------------------------------------------------------
INSERT INTO `ALLOCATION_GOAL` (allocation_goal_id, job_id, target_month, recommend_hour) VALUES
(1, 101, '2026-08', 20),
(2, 103, '2026-08', 8);

-- ------------------------------------------------------------
-- SAVING_GOAL (8월 저축 목표)
-- ------------------------------------------------------------
INSERT INTO `SAVING_GOAL` (user_id, target_month, target_amount, labor_intensity) VALUES
(1, '2026-08', 500000, 3);
