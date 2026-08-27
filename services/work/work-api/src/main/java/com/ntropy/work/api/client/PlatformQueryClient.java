package com.ntropy.work.api.client;

import java.util.List;

import com.ntropy.work.api.dto.summary.PlatformSummary;

public interface PlatformQueryClient {

    List<PlatformSummary> getPlatforms();

    PlatformSummary getPlatform(Long platformId);
}
