package com.ntropy.bff.dto.work.response;

import com.ntropy.work.api.dto.summary.PlatformMatchSummary;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PlatformMatchResponse {

    private Long platformId;
    private String platformName;
    private String depositName;

    public static PlatformMatchResponse from(PlatformMatchSummary summary) {
        PlatformMatchResponse response = new PlatformMatchResponse();
        response.platformId = summary.getPlatformId();
        response.platformName = summary.getPlatformName();
        response.depositName = summary.getDepositName();
        return response;
    }
}
