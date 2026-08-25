package com.santander.accountbalance.service;

import com.santander.accountbalance.dto.BalanceResponse;
import com.santander.accountbalance.exception.AccountNotFoundException;
import com.santander.accountbalance.model.Account;
import com.santander.accountbalance.model.Balance;
import com.santander.accountbalance.repository.AccountRepository;
import com.santander.accountbalance.repository.BalanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final BalanceRepository balanceRepository;
    private final AccountRepository accountRepository;
    private final ZoneId zoneIdSP;

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID accountId) {
        Balance balance = balanceRepository.findById(accountId)
                .orElseThrow(() -> notFound(accountId));

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> notFound(accountId));

        String updatedAt = balance.getUpdatedAt()
                .atZone(zoneIdSP).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        return new BalanceResponse(
                accountId,
                account.getOwner(),
                new BalanceResponse.BalanceInfo(balance.getAmount(), balance.getCurrency()),
                updatedAt);
    }

    private AccountNotFoundException notFound(UUID accountId) {
        return new AccountNotFoundException("Account not found: " + accountId);
    }

}
