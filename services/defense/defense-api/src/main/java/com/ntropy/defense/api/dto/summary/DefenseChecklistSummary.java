package com.ntropy.defense.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseChecklistSummary {
    private String code;
    private String title;
    private String description;
}
