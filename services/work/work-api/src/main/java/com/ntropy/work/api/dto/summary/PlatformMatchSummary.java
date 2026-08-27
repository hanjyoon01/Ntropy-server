package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 잡 등록 후보에 포함된 개별 플랫폼 매칭 정보를 노출하기 위한 공유 DTO. */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlatformMatchSummary {

    private Long platformId;
    private String platformName;
    private String depositName;
}
