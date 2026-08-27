package com.ntropy.bff.dto.defense.response;

import com.ntropy.defense.api.dto.summary.DefenseCauseSummary;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class DefenseCausesResponse {
    private List<DefenseCauseSummary> causes;
}
