package com.ntropy.defense.api.dto.command;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class DefenseModeReleaseCommand {
    private Long userId;
    private LocalDate returnDate;
}
