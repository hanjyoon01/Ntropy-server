package com.ntropy.user.api.dto;

import java.util.List;

/** 시딩된 가상회원 전체 목록과, 도메인 생성기가 함께 써야 하는 실행 컨텍스트. */
public record VirtualUserDataset(
        VirtualDatasetContext context,
        List<VirtualUserIdentity> users
) {
}
