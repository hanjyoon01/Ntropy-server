package com.ntropy.defense.api.dto.summary;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import com.ntropy.work.api.dto.summary.JobExpectedIncomeLossSummary;

import java.time.LocalDate;
import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ExpectedIncomeLossSummary {
    private Long totalAmount;
    private LocalDate periodStartDate;
    private LocalDate periodEndDate;
    private String calculationStatus;
    private List<JobExpectedIncomeLossSummary> jobs;
}
