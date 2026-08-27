package com.ntropy.account.domain.entity;

import java.time.LocalDateTime;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * CODEF 커넥티드 아이디(connectedId)와 사용자를 매핑하는 도메인 객체.
 * 실제 은행/카드 로그인 정보는 CODEF가 보관하며, 여기서는 connectedId만 저장한다.
 */
@Getter
@Setter
@NoArgsConstructor
public class CodefConnection {

    private Long id;
    private Long userId;

    /**
     * 연결 제공자 (CODEF: 실제 연동, NTROPY: 가상 연결). {@link com.ntropy.account.domain.ConnectionProvider}의 name() 값.
     */
    private String provider;

    private String connectedId;

    /**
     * 등록 완료된 기관코드 JSON 배열 원문(예: {@code ["0004","0088"]}).
     * 동일 기관에 대한 CODEF {@code /account/add} 중복 요청을 막는 데 쓰인다.
     * 파싱/직렬화는 {@link com.ntropy.account.domain.InstitutionKeys}를 사용한다.
     */
    private String registeredInstitutionKeys;

    /** 기업·국민은행 birthDate AES-256-GCM 암호문(Base64). {@link com.ntropy.account.security.BirthDateCipher} 참고. */
    private String birthDateCiphertext;

    /** 암호화마다 새로 생성하는 12바이트 IV(Base64). */
    private String birthDateIv;

    /** 암호화 당시 사용한 키 버전. 키 회전 시 복호화에 필요. */
    private Integer birthDateKeyVersion;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
