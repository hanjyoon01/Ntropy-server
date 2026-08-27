package com.ntropy.defense.adapter.diagnosis;

import org.springframework.stereotype.Component;

import com.ntropy.defense.port.diagnosis.DefenseDiagnosisSnapshot;
import com.ntropy.defense.port.diagnosis.DiagnosisSnapshotPort;
import com.ntropy.diagnosis.api.client.DiagnosisQueryClient;
import com.ntropy.diagnosis.api.dto.DiagnosisDefenseSnapshot;

import lombok.RequiredArgsConstructor;

/** diagnosis-service가 발행한 DiagnosisQueryClient를 defense의 포트로 번역한다. */
@Component
@RequiredArgsConstructor
public class DiagnosisSnapshotAdapter implements DiagnosisSnapshotPort {

    private final DiagnosisQueryClient diagnosisQueryClient;

    @Override
    public DefenseDiagnosisSnapshot getDefenseSnapshot(Long userId) {
        DiagnosisDefenseSnapshot snapshot = diagnosisQueryClient.getDefenseSnapshot(userId);
        if (snapshot == null) {
            return null;
        }
        return new DefenseDiagnosisSnapshot(
                snapshot.getReserveAmount(), snapshot.getSafeAssetAmount(), snapshot.getAverageMonthlyExpense());
    }
}
