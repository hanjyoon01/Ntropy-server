package com.ntropy.work.api.client;

import java.util.List;

import com.ntropy.work.api.dto.summary.CategorySummary;

public interface CategoryQueryClient {

    List<CategorySummary> getCategories();

    CategorySummary getCategory(Long categoryId);
}
