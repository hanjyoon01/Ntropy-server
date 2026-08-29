package com.ntropy.bff.dto.defense.response;

import com.ntropy.defense.api.dto.summary.GrowthModeSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class GrowthModeResponse {
    private Boolean isPaused;
    private String integrationStatus;
    private String message;

    public static GrowthModeResponse from(GrowthModeSummary summary) {
        if (summary == null) {
            return null;
        }
        return new GrowthModeResponse(
                summary.getIsPaused(), summary.getIntegrationStatus(), summary.getMessage());
    }
}
