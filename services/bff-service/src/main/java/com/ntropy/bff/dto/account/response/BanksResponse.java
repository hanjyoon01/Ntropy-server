package com.ntropy.bff.dto.account.response;

import java.util.List;

import com.ntropy.account.api.dto.BankSummary;

public record BanksResponse(List<BankSummary> banks) {
}
