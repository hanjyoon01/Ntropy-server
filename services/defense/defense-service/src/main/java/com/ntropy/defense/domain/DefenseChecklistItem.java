package com.ntropy.defense.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DefenseChecklistItem {
    SECURE_SAFETY("안전 확보", "긴급한 위험이 있다면 안전한 장소로 이동하고 119 또는 관계기관에 도움을 요청하세요."),
    RECORD_INCIDENT("사고 자료 보관", "산재·보험 신청에 대비해 사고 시각, 현장 사진과 플랫폼 업무 기록 보관하기"),
    VISIT_MEDICAL_PROVIDER("진료 증빙 보관", "보험 청구에 대비해 진단서와 영수증을 보관하고 <a href=\"https://www.e-gen.or.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">의료기관 찾기 ↗</a> 이용하기"),
    CHECK_WORK_ACCIDENT_COVERAGE("산재보험 확인", "업무 또는 업무 관련 이동 중 사고라면 <a href=\"https://webzine.comwel.or.kr/vol152/sub02.html\" target=\"_blank\" rel=\"noopener noreferrer\">산재보험 적용 여부 ↗</a> 확인하기"),
    FOLLOW_TREATMENT_PLAN("치료 계획 확인", "예상 치료기간과 근무 가능 시점을 의료진과 확인하세요."),
    REVIEW_HEALTH_COVERAGE("보장 내역 확인", "<a href=\"https://www.nhis.or.kr/static/html/wbda/g/wbdag0103.html\" target=\"_blank\" rel=\"noopener noreferrer\">건강보험·실손보험 보장 내용 ↗</a>과 청구 서류 확인하기"),
    SAVE_RESTRICTION_NOTICE("정지 사유 확인", "플랫폼이 안내한 정지 사유와 해제 예정일 확인하기"),
    FILE_PLATFORM_APPEAL("이의제기 준비", "제출 기한을 확인하고 배차·정산·평가 내역 등 증빙자료 준비하기"),
    SAVE_WORK_HISTORY("정산·상담 확인", "미정산 금액과 지급일을 확인하고 필요하면 <a href=\"https://www.moel.go.kr/news/enews/report/enewsView.do?news_seq=19195\" target=\"_blank\" rel=\"noopener noreferrer\">프리랜서SOS 안내 ↗</a> 확인하기"),
    ASSESS_REPAIR("수리 범위 확인", "차량 또는 장비의 고장 원인, 수리비와 예상 수리기간을 확인하세요."),
    CHECK_RENTAL_OPTION("대체 장비 확인", "수리기간 동안 이용할 수 있는 렌트·대체 장비와 비용을 확인하세요."),
    CHECK_DAMAGE_COVERAGE("손해 보장 확인", "자동차보험·장비보험·제조사 보증 등 적용 가능한 보장을 확인하세요. 수리비 또는 품질보증 관련 분쟁은 <a href=\"https://www.consumer.go.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">소비자 상담·피해구제 ↗</a>를 이용하세요."),
    ESTIMATE_CARE_PERIOD("돌봄 기간 확인", "직접 돌봄이 필요한 기간과 대체 돌봄 가능 시점 확인하기"),
    CHECK_CARE_SUPPORT("돌봄 지원 확인", "이용 시간과 비용을 비교하고 <a href=\"https://www.idolbom.go.kr/front/\" target=\"_blank\" rel=\"noopener noreferrer\">아이돌봄서비스 ↗</a> 확인하기"),
    RECORD_OTHER_CAUSE("상황 기록", "근무가 어려워진 사유와 시작일, 관련 증빙자료를 정리해 두세요."),
    SAVE_MEDICAL_DOCUMENTS("의료 증빙 보관", "보험 청구에 대비해 진단서, 처방전과 의료비 영수증 보관하기"),
    CHECK_ILLNESS_RETURN_TIMING("복귀 시점 확인", "치료 일정과 회복기간을 기준으로 예상 복귀일 정하기"),
    CHECK_FATIGUE_CONDITION("피로 상태 확인", "앱에서 현재 피로도와 휴식 필요 여부 확인하기"),
    SET_RECOVERY_PERIOD("회복 기간 설정", "충분한 회복 기간을 설정해 주세요."),
    CHECK_MEDICAL_CARE_FOR_FATIGUE("지속 증상 확인", "휴식 후에도 증상이 계속되면 <a href=\"https://www.e-gen.or.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">의료기관 찾기 ↗</a> 이용하기"),
    PLAN_PHYSICAL_WORK_RETURN("근무 복귀 계획", "회복 후 짧은 근무부터 시작해 단계적으로 시간 늘리기"),
    CHECK_MENTAL_CONDITION("마음 상태 확인", "<a href=\"https://www.mentalhealth.go.kr/portal/mdexmn/getMdexmnuserJoin.do\" target=\"_blank\" rel=\"noopener noreferrer\">마음 건강 자가검진 ↗</a>으로 현재 상태 살피기"),
    IDENTIFY_STRESS_FACTORS("스트레스 원인 확인", "스트레스를 높이는 원인을 정리해 주세요."),
    CHECK_MENTAL_HEALTH_SUPPORT("상담 지원 확인", "어려움이 지속되면 <a href=\"https://www.mentalhealth.go.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">정신건강 관련기관 찾기 ↗</a> 이용하기"),
    PLAN_MENTAL_WORK_RETURN("근무 복귀 계획", "짧은 근무부터 시작해 마음 상태에 따라 시간 늘리기"),
    CHECK_CARE_WORK_TIME("근무 가능 시간 확인", "병원과 등·하원 일정을 반영해 근무 가능 시간 계획하기"),
    CHECK_FAMILY_SUPPORT("돌봄 일정 분담", "가족과 돌봄 일정을 분담해 주세요."),
    CHECK_FIXED_COSTS("고정비 점검", "사용하지 않는 구독과 요금제의 중단·변경 가능 여부 확인하기"),
    CHECK_WELFARE_SUPPORT("생활비·지원 확인", "휴식기간의 필수 생활비를 계산하고 <a href=\"https://www.bokjiro.go.kr/\" target=\"_blank\" rel=\"noopener noreferrer\">복지 지원서비스 ↗</a> 확인하기"),
    CHECK_PERSONAL_SCHEDULE_END("일정 종료일 확인", "근무를 어렵게 하는 주요 일정이 끝나는 시점 확인하기"),
    ORGANIZE_PERSONAL_SCHEDULE("개인 일정 정리", "방어기간에 처리할 일정을 정리해 주세요."),
    REVIEW_RETURN_DATE("복귀일 재확인", "예상 복귀일이 현실적인지 확인하고 변경이 필요하면 방어모드 기간을 조정하세요.");

    private final String title;
    private final String description;
}
