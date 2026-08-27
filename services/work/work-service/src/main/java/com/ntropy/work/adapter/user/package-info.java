/**
 * work-service가 소유한 아웃바운드 어댑터 계층. port.user의 인터페이스를 구현하며,
 * 내부적으로만 user-service가 발행한 계약(common.client.ActiveUserQueryClient)을 호출한다.
 */
package com.ntropy.work.adapter.user;
