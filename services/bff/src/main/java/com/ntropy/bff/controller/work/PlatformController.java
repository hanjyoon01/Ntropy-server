package com.ntropy.bff.controller.work;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.work.response.PlatformsResponse;
import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.work.api.client.PlatformQueryClient;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

@Api(tags = "플랫폼")
@RestController
@RequestMapping("/api/platforms")
@RequiredArgsConstructor
public class PlatformController {

    private final PlatformQueryClient platformQueryClient;

    @ApiOperation("플랫폼 목록 조회")
    @GetMapping
    public ApiResponse<PlatformsResponse> getPlatforms() {
        return ApiResponse.success(new PlatformsResponse(platformQueryClient.getPlatforms()));
    }
}
