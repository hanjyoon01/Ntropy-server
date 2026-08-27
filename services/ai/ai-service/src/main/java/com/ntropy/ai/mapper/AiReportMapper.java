package com.ntropy.ai.mapper;

import com.ntropy.ai.domain.AiReport;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI_REPORT 테이블 접근을 위한 MyBatis Mapper 인터페이스
 * 메서드 이름과 AiReportMapper.xml의 id 값이 1:1로 매핑됨
 */
@Mapper
public interface AiReportMapper {

    /**
     * AI 월간 리포트 신규 데이터를 DB에 적재
     * @param aiReport 저장할 AI 리포트 객체
     * @return 영향받은 행(Row) 수
     */
    int insert(AiReport aiReport);

    /**
     * 사용자·연월 기준으로 AI 리포트를 저장하거나 갱신합니다.
     *
     * (user_id, year_month) 유니크 제약조건이 충돌하면
     * 기존 리포트의 JSON 데이터만 갱신합니다.
     *
     * @param aiReport 저장 또는 갱신할 AI 리포트 객체
     * @return 영향받은 행 수
     */
    int upsert(AiReport aiReport);



    /**
     * 리포트 고유 ID(PK) 기준으로 단건 조회
     * @param reportId 조회할 리포트 PK
     * @return AI 리포트 객체
     */
    AiReport findById(@Param("reportId") Long reportId);

    /**
     * 특정 유저의 해당 연월(YYYY-MM) AI 리포트 조회
     * @param userId 유저 고유 ID
     * @param yearMonth 대상 연월 (예: "2026-08")
     * @return 해당 연월의 AI 리포트 객체
     */
    AiReport findByUserIdAndYearMonth(@Param("userId") Long userId, @Param("yearMonth") String yearMonth);

    /**
     * 특정 유저의 과거 전체 AI 리포트 이력 목록 조회
     * @param userId 유저 고유 ID
     * @return 유저의 AI 리포트 전체 리스트 (최신순 정렬)
     */
    List<AiReport> findAllByUserId(@Param("userId") Long userId);
}