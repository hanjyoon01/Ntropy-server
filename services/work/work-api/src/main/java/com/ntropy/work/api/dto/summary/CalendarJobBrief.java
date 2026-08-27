package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월간 캘린더의 하루에 표시되는 잡 요약(이름만). 급여 등 민감정보는 담지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CalendarJobBrief {

    private Long jobId;
    private String jobName;
}
