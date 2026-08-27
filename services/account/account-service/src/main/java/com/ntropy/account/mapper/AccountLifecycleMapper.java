package com.ntropy.account.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AccountLifecycleMapper {

    int deactivateByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    int activateByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);
}
