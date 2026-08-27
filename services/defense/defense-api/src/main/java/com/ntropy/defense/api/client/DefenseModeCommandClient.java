package com.ntropy.defense.api.client;

import com.ntropy.defense.api.dto.command.DefenseModeEnterCommand;
import com.ntropy.defense.api.dto.command.DefenseModeReleaseCommand;
import com.ntropy.defense.api.dto.summary.DefenseModeSummary;

public interface DefenseModeCommandClient {
    DefenseModeSummary enter(DefenseModeEnterCommand command);
    DefenseModeSummary release(Long defenseId, DefenseModeReleaseCommand command);
}
