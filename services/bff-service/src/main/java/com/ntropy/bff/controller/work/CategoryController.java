package com.ntropy.bff.controller.work;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.response.CategoriesResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.work.api.client.CategoryQueryClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

@Api(tags = "카테고리")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryQueryClient categoryQueryClient;

    @ApiOperation("카테고리 목록 조회")
    @GetMapping
    public ApiResponse<CategoriesResponse> getCategories() {
        return ApiResponse.success(new CategoriesResponse(categoryQueryClient.getCategories()));
    }
}
