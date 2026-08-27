package com.ntropy.account.mapper;

import java.util.List;

import com.ntropy.account.domain.entity.CodefConnection;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodefConnectionMapper {

    void insert(CodefConnection codefConnection);

    void insertIfAbsent(CodefConnection codefConnection);

    void upsert(CodefConnection codefConnection);

    CodefConnection findByUserIdAndProvider(@Param("userId") Long userId, @Param("provider") String provider);

    /** 일일 동기화의 사용자별 단건 조회를 대체하는 chunk 단위 일괄 조회 (이슈 #233). */
    List<CodefConnection> findByUserIdsAndProvider(@Param("userIds") List<Long> userIds,
                                                    @Param("provider") String provider);
}
