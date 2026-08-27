package com.ntropy.work.domain;

/** 잡 등록 후보에 포함된 개별 플랫폼 매칭 정보. */
public record PlatformMatch(Long platformId, String platformName, String depositName) {
}
