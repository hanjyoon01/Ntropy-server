package com.ntropy.work.client;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.CategoryQueryClient;
import com.ntropy.work.api.dto.summary.CategorySummary;
import com.ntropy.work.domain.entity.Category;
import com.ntropy.work.service.CategoryService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalCategoryQueryClient implements CategoryQueryClient {

    private final CategoryService categoryService;

    @Override
    public List<CategorySummary> getCategories() {
        return categoryService.findAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public CategorySummary getCategory(Long categoryId) {
        return toSummary(categoryService.findById(categoryId));
    }

    private CategorySummary toSummary(Category category) {
        return CategorySummary.builder()
                .categoryId(category.getCategoryId())
                .name(category.getName())
                .build();
    }
}
