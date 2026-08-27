-- ============================================================
-- work-service 마스터 데이터 시딩 (CATEGORY, PLATFORM)
-- Issue #2 체크리스트: 카테고리 목록 확정, 플랫폼별 정산주기/정산처명 시드
-- task_per_hour는 JOB 테이블로 이동함 (카테고리가 아니라 잡별로 다를 수 있어서)
-- ============================================================

INSERT INTO `CATEGORY` (category_id, name) VALUES
(1, '배달'),
(2, '대리운전'),
(3, '택배/물류 상하차'),
(4, '가사·청소 도우미'),
(5, '펫시터·돌봄'),
(6, '콘텐츠 제작');

-- deposit_name은 입금 거래내역과 대조되는 실제 정산처명 기준.
-- category_id: 플랫폼이 속한 카테고리 FK (배달의민족/쿠팡이츠/요기요=배달, 카카오T대리=대리운전 등)
-- settlement_cycle 값 예시: DAILY(익일), WEEKLY(주급), MONTHLY(월급)
-- settlement_offset_day는 정산 기준일로부터 실제 입금까지 며칠 걸리는지(단위는 settlement_offset_unit).
--   DAILY 전용이 아니라 WEEKLY(쿠팡이츠/쿠팡플렉스/펫플래닛 등)에도 settlement_day_of_week와 함께 쓰여
--   "정산기간 종료 후 며칠 뒤, 그 요일에" 입금되는지를 표현함.
-- settlement_offset_unit은 "플랫폼 DB" 문서에 "영업일"이라고 명시된 것만 BUSINESS_DAY로 구분
--   (배민커넥트 "3영업일 뒤", 쿠팡이츠 배달파트너 "영업일 기준 3일 후"). 나머지는 문서에 "익일"로만
--   적혀 있어 영업일 여부가 불명확하므로 기본값 CALENDAR_DAY 유지. 요일/일자 고정형(WEEKLY 요일 지정,
--   MONTHLY)은 이 필드를 쓰지 않음 — 기본값 그대로 둠.
--   BUSINESS_DAY는 현재 계산 로직(SettlementPeriodCalculator)에 아직 반영 안 됨 — 공휴일 API 연동
--   이슈(정산 기간 계산 시 주말/공휴일 반영) 완료 후 실제로 영업일 계산에 쓰일 예정.
-- settlement_day_of_week/settlement_day_of_month: 쿠팡플렉스(공식 사이트 확인: 화~차주 월 배송분을
--   차주 월 기준 D+4일 금요일 정산)만 실제 확인된 값이고, 나머지 WEEKLY/MONTHLY 플랫폼은
--   "플랫폼 DB" 문서 기준 추정값임 — 팀 확인 전까지 실제 값으로 취급 금지.
--   또한 문서 자체에서 아래 2가지는 의도적으로 단순화함:
--   1) 출금 신청형(카카오T대리/티맵 대리)은 익일 정산으로 단순화
--   2) 월단위 정산에서 5~15일 사이에 정산되는 경우(로젠택배)는 5일 정산으로 단순화
-- settlement_trigger_type: AUTO(플랫폼이 정산주기에 따라 자동으로 계좌 입금)/ON_DEMAND(운행 종료 즉시
--   포인트로만 적립되고, 사용자가 임의 시점에 임의 금액으로 출금 신청해야 계좌 입금 - 실입금 거래와
--   특정 근무일지를 매칭할 수 없음). 카카오T대리/티맵 대리만 ON_DEMAND, 나머지는 기본값 AUTO.
--   ON_DEMAND는 WorkLog 확정 시점에 실거래 매칭 없이 즉시 정산완료 처리하는 방식으로 다룸
--   (SettlementService.completeOnDemandSettlement) — 이 잡들은 배치의 실거래 매칭 대상에서도 제외됨.
-- 입금처명이 "n.a."로 표시된 플랫폼(요기요라이더/티맵 대리/청소연구소/케어닥/펫플래닛)은
--   운영사명 기준 추정치이며 실제 마이데이터 연동 후 확인 필요.
-- 로지올은 실제로는 실시간 정산 + 온디맨드 출금 구조라 WEEKLY 모델과 안 맞아 시딩에서 제외함
-- category_id는 위 CATEGORY 6개 기준 (1배달/2대리운전/3택배·물류 상하차/4가사·청소 도우미/5펫시터·돌봄/6콘텐츠 제작)
INSERT INTO `PLATFORM`
    (platform_id, category_id, platform_name, deposit_name, settlement_cycle, settlement_trigger_type,
     settlement_offset_day, settlement_offset_unit, settlement_day_of_week, settlement_day_of_month)
VALUES
(1, 1, '배민커넥트', '우아한청년들', 'DAILY', 'AUTO', 3, 'BUSINESS_DAY', NULL, NULL),
(2, 1, '쿠팡이츠 배달파트너', '쿠팡이츠정산', 'WEEKLY', 'AUTO', 3, 'BUSINESS_DAY', 'FRI', NULL),
(3, 1, '요기요라이더', '위대한상상', 'WEEKLY', 'AUTO', 15, 'CALENDAR_DAY', 'MON', NULL),
(4, 2, '카카오T대리', '카카오모빌리티', 'DAILY', 'ON_DEMAND', 1, 'CALENDAR_DAY', NULL, NULL),
(5, 6, '유튜브', 'GOOGLE', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 21),
(7, 3, '쿠팡플렉스', '쿠팡-용역비', 'WEEKLY', 'AUTO', 4, 'CALENDAR_DAY', 'FRI', NULL),
(8, 2, '티맵 대리', '티맵모빌리티', 'DAILY', 'ON_DEMAND', 1, 'CALENDAR_DAY', NULL, NULL),
(9, 3, 'CJ대한통운 상하차', 'CJ대한통운', 'DAILY', 'AUTO', 1, 'CALENDAR_DAY', NULL, NULL),
(10, 3, '로젠택배', '로젠택배', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 5),
(11, 4, '청소연구소(청연)', '생활연구소', 'DAILY', 'AUTO', 1, 'CALENDAR_DAY', NULL, NULL),
(12, 4, '미소', '유한회사미소', 'DAILY', 'AUTO', 1, 'CALENDAR_DAY', NULL, NULL),
(13, 5, '케어닥', '케어닥', 'WEEKLY', 'AUTO', 1, 'CALENDAR_DAY', 'MON', NULL),
(14, 5, '펫플래닛', '펫피플', 'WEEKLY', 'AUTO', 3, 'CALENDAR_DAY', 'WED', NULL),
(15, 6, '틱톡', 'PAYPAL', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 15),
(16, 6, '네이버TV', '네이버', 'MONTHLY', 'AUTO', NULL, 'CALENDAR_DAY', NULL, 6);