package com.ntropy.user.api.client;

import com.ntropy.user.api.dto.VirtualUserDataset;

/** user-service가 시딩한 가상회원 목록을 다른 서비스 모듈이 조회하는 계약. */
public interface VirtualUserQueryClient {

    VirtualUserDataset findSeededVirtualUsers();
}
