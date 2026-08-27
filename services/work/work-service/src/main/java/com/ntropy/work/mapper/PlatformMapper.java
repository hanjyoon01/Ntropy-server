package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ntropy.work.domain.entity.Platform;

@Mapper
public interface PlatformMapper {

    void insert(Platform platform);

    Platform findById(Long platformId);

    List<Platform> findAll();

    void update(Platform platform);

    void deleteById(Long platformId);
}
