package com.ntropy.bff.dto.account.response;

import java.util.List;

import com.ntropy.account.api.dto.AccountSummary;

public record AccountsResponse(List<AccountSummary> accounts) {
}
