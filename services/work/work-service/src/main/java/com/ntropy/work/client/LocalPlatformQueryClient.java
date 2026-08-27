package com.ntropy.work.client;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.ntropy.work.api.client.PlatformQueryClient;
import com.ntropy.work.api.dto.summary.PlatformSummary;
import com.ntropy.work.domain.entity.Platform;
import com.ntropy.work.service.PlatformService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class LocalPlatformQueryClient implements PlatformQueryClient {

    private final PlatformService platformService;

    @Override
    public List<PlatformSummary> getPlatforms() {
        return platformService.findAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    public PlatformSummary getPlatform(Long platformId) {
        return toSummary(platformService.findById(platformId));
    }

    private PlatformSummary toSummary(Platform platform) {
        return PlatformSummary.builder()
                .platformId(platform.getPlatformId())
                .categoryId(platform.getCategoryId())
                .platformName(platform.getPlatformName())
                .settlementCycle(platform.getSettlementCycle())
                .settlementOffsetDay(platform.getSettlementOffsetDay())
                .settlementDayOfWeek(platform.getSettlementDayOfWeek())
                .settlementDayOfMonth(platform.getSettlementDayOfMonth())
                .build();
    }
}
