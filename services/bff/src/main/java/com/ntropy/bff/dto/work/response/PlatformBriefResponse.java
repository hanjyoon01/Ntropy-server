package com.ntropy.bff.dto.work.response;

import com.ntropy.work.api.dto.summary.PlatformBrief;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PlatformBriefResponse {

    private Long platformId;
    private String platformName;

    public static PlatformBriefResponse from(PlatformBrief brief) {
        PlatformBriefResponse response = new PlatformBriefResponse();
        response.platformId = brief.getPlatformId();
        response.platformName = brief.getPlatformName();
        return response;
    }
}
