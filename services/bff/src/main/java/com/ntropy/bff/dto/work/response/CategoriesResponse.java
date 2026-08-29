package com.ntropy.bff.dto.work.response;

import java.util.List;

import com.ntropy.work.api.dto.summary.CategorySummary;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * CATEGORY는 민감도 낮은 마스터데이터라 common DTO를 그대로 감싸서 노출한다.
 */
@Getter
@AllArgsConstructor
public class CategoriesResponse {

    private List<CategorySummary> categories;
}
