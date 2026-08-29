package com.ntropy.bff.controller.work;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ntropy.bff.dto.common.ApiResponse;
import com.ntropy.work.api.client.WeatherQueryClient;
import com.ntropy.work.api.dto.summary.WeatherForecastList;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;

@Api(tags = "날씨")
@RestController
@RequestMapping("/api/weather")
@RequiredArgsConstructor
public class WeatherController {

    private final WeatherQueryClient weatherQueryClient;

    @ApiOperation("단기예보 조회")
    @GetMapping
    public ApiResponse<WeatherForecastList> getForecasts(
            @RequestParam(required = false) Double latitude,
            @RequestParam(required = false) Double longitude
    ) {
        return ApiResponse.success(weatherQueryClient.getForecasts(latitude, longitude));
    }
}
