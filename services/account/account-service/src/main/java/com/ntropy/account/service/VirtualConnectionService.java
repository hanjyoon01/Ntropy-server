package com.ntropy.account.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.ntropy.account.domain.ConnectionProvider;
import com.ntropy.account.domain.InstitutionKeys;
import com.ntropy.account.domain.VirtualConnectedId;
import com.ntropy.account.domain.entity.CodefConnection;
import com.ntropy.account.mapper.CodefConnectionMapper;

import lombok.RequiredArgsConstructor;

/**
 * 실제 CODEF API를 호출하지 않는 사용자별 가상(NTROPY) 연결을 발급/재사용한다 (이슈 #35).
 * 가상 connectedId는 내부 가상 데이터 처리에만 쓰이며 실제 CODEF API에는 전달하지 않는다.
 * 실 CODEF 연동({@link CodefConnectionService})과는 provider로 분리된 별도 행을 사용해 서로 간섭하지 않는다.
 */
@Service
@RequiredArgsConstructor
public class VirtualConnectionService {

    private final CodefConnectionMapper codefConnectionMapper;

    public CodefConnection getOrCreateConnection(Long userId) {
        CodefConnection existing = findVirtualConnection(userId);
        if (existing != null) {
            return existing;
        }

        CodefConnection connection = new CodefConnection();
        connection.setUserId(userId);
        connection.setProvider(ConnectionProvider.NTROPY.name());
        connection.setConnectedId(VirtualConnectedId.generate());
        codefConnectionMapper.insertIfAbsent(connection);

        CodefConnection saved = findVirtualConnection(userId);
        if (saved == null) {
            throw new IllegalStateException("가상 연결 저장 확인 실패");
        }
        return saved;
    }

    public CodefConnection registerInstitution(CodefConnection connection, String organizationCode) {
        List<String> registeredKeys = InstitutionKeys.parse(connection.getRegisteredInstitutionKeys());
        if (registeredKeys.contains(organizationCode)) {
            return connection;
        }

        registeredKeys.add(organizationCode);
        connection.setRegisteredInstitutionKeys(InstitutionKeys.serialize(registeredKeys));
        codefConnectionMapper.upsert(connection);

        return findVirtualConnection(connection.getUserId());
    }

    private CodefConnection findVirtualConnection(Long userId) {
        return codefConnectionMapper.findByUserIdAndProvider(userId, ConnectionProvider.NTROPY.name());
    }
}
