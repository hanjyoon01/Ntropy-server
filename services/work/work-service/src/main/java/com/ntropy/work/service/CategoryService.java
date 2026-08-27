package com.ntropy.work.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.common.exception.ServiceException;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.exception.WorkErrorCode;
import com.ntropy.work.mapper.CategoryMapper;

import lombok.RequiredArgsConstructor;

/**
 * CATEGORY는 마스터 데이터라 조회만 제공한다. (등록/수정은 시딩으로만 관리)
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryMapper categoryMapper;

    public List<Category> findAll() {
        return categoryMapper.findAll();
    }

    public Category findById(Long categoryId) {
        Category category = categoryMapper.findById(categoryId);
        if (category == null) {
            throw new ServiceException(WorkErrorCode.CATEGORY_NOT_FOUND, "categoryId=" + categoryId);
        }
        return category;
    }
}
