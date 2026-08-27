package com.ntropy.work.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;

import com.ntropy.work.domain.entity.Category;

@Mapper
public interface CategoryMapper {

    void insert(Category category);

    Category findById(Long categoryId);

    List<Category> findAll();

    void update(Category category);

    void deleteById(Long categoryId);
}
