package com.ntropy.work.mapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ntropy.work.domain.entity.Category;

/**
 * 테스트용 인메모리 CategoryMapper 구현체. 여러 서비스 테스트에서 공용으로 사용한다.
 */
public class InMemoryCategoryMapper implements CategoryMapper {

    private final Map<Long, Category> store = new LinkedHashMap<>();

    public void seed(Category category) {
        store.put(category.getCategoryId(), category);
    }

    @Override
    public void insert(Category category) {
        store.put(category.getCategoryId(), category);
    }

    @Override
    public Category findById(Long categoryId) {
        return store.get(categoryId);
    }

    @Override
    public List<Category> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void update(Category category) {
        store.put(category.getCategoryId(), category);
    }

    @Override
    public void deleteById(Long categoryId) {
        store.remove(categoryId);
    }
}
