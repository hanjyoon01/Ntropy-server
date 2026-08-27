package com.ntropy.defense.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseCauseSummary {
    private String causeCode;
    private String causeGroup;
    private String causeName;
    private List<String> checklist;
    private String guideMessage;
}
