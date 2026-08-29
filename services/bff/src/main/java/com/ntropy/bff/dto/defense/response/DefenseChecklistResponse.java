package com.ntropy.bff.dto.defense.response;

import com.ntropy.defense.api.dto.summary.DefenseChecklistSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class DefenseChecklistResponse {
    private String code;
    private String title;
    private String description;

    public static DefenseChecklistResponse from(DefenseChecklistSummary summary) {
        return new DefenseChecklistResponse(summary.getCode(), summary.getTitle(), summary.getDescription());
    }
}
