package com.ntropy.account.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.ntropy.account.api.dto.TransactionAnalysisSaveItem;
import com.ntropy.account.api.dto.TransactionAnalysisSaveRequest;
import com.ntropy.account.domain.entity.TxnAnalysis;

@Mapper
public interface TxnAnalysisMapper {

    /**
     * 거래 분석 결과 한 건을 저장하거나 갱신합니다.
     */
    int upsert(TxnAnalysis txnAnalysis);

    /**
     * 기존 월간 소비 분류 흐름에서 전달된 결과를 일괄 저장합니다.
     */
    int upsertAnalyses(
            TransactionAnalysisSaveRequest request
    );

    /**
     * 일간 소비 분류 배치에서 생성한 소비·비소비 결과를
     * 일괄 저장합니다.
     */
    int upsertDailyAnalyses(
            @Param("analyses")
            List<TransactionAnalysisSaveItem> analyses
    );
}