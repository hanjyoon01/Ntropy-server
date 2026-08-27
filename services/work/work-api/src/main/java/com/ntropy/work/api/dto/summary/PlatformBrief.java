package com.ntropy.work.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 잡에 연결된 플랫폼 요약(이름만). 정산 주기 등 상세 정보는 담지 않는다.
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PlatformBrief {

    private Long platformId;
    private String platformName;
}
