package com.ntropy.bff.dto.work.request;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.ntropy.work.api.dto.command.JobRegisterCommand;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class JobCreateRequest {

    /**
     * 사용자 입력 미허용, PER_TASK 잡의 시간당 예상 처리 건수는 카테고리별 기본값으로 고정한다
     * (2026-08). 택배/물류 상하차, 펫시터·돌봄, 콘텐츠 제작은 실측치가 없어 대략치로 채운
     * 임시값이다 - 프론트(김동현) 확인 전까지 정확도 보장 안 됨.
     */
    private static final Map<Long, Float> DEFAULT_TASK_PER_HOUR_BY_CATEGORY = Map.of(
            1L, 3.5f,   // 배달
            2L, 1.5f,   // 대리운전
            3L, 21f,    // 택배/물류 상하차 (대략치)
            4L, 0.25f,  // 가사·청소 도우미
            5L, 0.5f,   // 펫시터·돌봄 (대략치)
            6L, 0.3f   // 콘텐츠 제작 (대략치)
    );

    private Long categoryId;
    private String jobName;
    private String settlementType;
    private Integer hourlyWage;
    private Integer monthlyWage;
    private Integer perTaskWage;
    private Boolean isRegular;
    private Integer baseFatigue;
    private List<Long> platformIds;
    private List<JobScheduleRequest> schedules;

    public JobRegisterCommand toCommand(Long userId) {
        List<JobScheduleRequest> safeSchedules = schedules == null ? Collections.emptyList() : schedules;
        return new JobRegisterCommand(
                userId,
                categoryId,
                jobName,
                settlementType,
                hourlyWage,
                monthlyWage,
                perTaskWage,
                "PER_TASK".equals(settlementType) ? DEFAULT_TASK_PER_HOUR_BY_CATEGORY.get(categoryId) : null,
                isRegular,
                baseFatigue,
                platformIds,
                safeSchedules.stream()
                        .map(JobScheduleRequest::toCommand)
                        .collect(Collectors.toList())
        );
    }
}
