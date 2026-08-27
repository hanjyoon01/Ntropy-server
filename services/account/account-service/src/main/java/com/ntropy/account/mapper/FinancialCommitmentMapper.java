package com.ntropy.account.mapper;

import java.time.LocalDate;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.mapper.projection.InsuranceOutflowRow;
import com.ntropy.account.mapper.projection.LoanCommitmentCandidateRow;
import com.ntropy.account.mapper.projection.SavingCommitmentCandidateRow;

/** 방어모드용 금융 납입 예정 판정에 필요한 원시 데이터를 조회하는 MyBatis Mapper입니다. */
@Mapper
public interface FinancialCommitmentMapper {

    List<SavingCommitmentCandidateRow> findSavingCommitmentCandidates(@Param("userId") Long userId);

    /**
     * loanDisbursementKeywords는 LOAN 신규·실행·증액(대출금 지급) 판정에 사용되며,
     * 호출 측은 com.ntropy.account.api.domain.LoanDisbursementKeywords.KEYWORDS를 전달해야 합니다.
     */
    List<LoanCommitmentCandidateRow> findLoanCommitmentCandidates(
            @Param("userId") Long userId,
            @Param("loanDisbursementKeywords") List<String> loanDisbursementKeywords
    );

    List<InsuranceOutflowRow> findInsuranceOutflowCandidates(
            @Param("userId") Long userId,
            @Param("observationStartDate") LocalDate observationStartDate,
            @Param("observationEndDate") LocalDate observationEndDate
    );
}
