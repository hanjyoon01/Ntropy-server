package com.ntropy.account.mapper;

import com.ntropy.account.domain.entity.CodefToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CodefTokenMapper {

    void insert(CodefToken codefToken);

    CodefToken findLatest(@Param("serviceType") String serviceType, @Param("clientId") String clientId);
}
