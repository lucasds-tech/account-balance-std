package com.santander.accountbalance.service;

import com.santander.accountbalance.dto.BalanceResponse;
import com.santander.accountbalance.exception.AccountNotFoundException;
import com.santander.accountbalance.model.Account;
import com.santander.accountbalance.model.Balance;
import com.santander.accountbalance.repository.AccountRepository;
import com.santander.accountbalance.repository.BalanceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private BalanceRepository balanceRepository;

    @Mock
    private AccountRepository accountRepository;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(balanceRepository, accountRepository,
                ZoneId.of("America/Sao_Paulo"));
    }

    @Test
    void shouldReturnBalanceWhenAccountExists() {
        UUID accountId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-08-03T15:10:20.432Z");

        Balance balance = new Balance(accountId, new BigDecimal("278.10"), "BRL", updatedAt);
        Account account = new Account(accountId, ownerId, "ENABLED", updatedAt);

        when(balanceRepository.findById(accountId)).thenReturn(Optional.of(balance));
        when(accountRepository.findById(accountId)).thenReturn(Optional.of(account));

        BalanceResponse response = balanceService.getBalance(accountId);

        assertThat(response.id()).isEqualTo(accountId);
        assertThat(response.owner()).isEqualTo(ownerId);
        assertThat(response.balance().amount()).isEqualByComparingTo(new BigDecimal("278.10"));
        assertThat(response.balance().currency()).isEqualTo("BRL");

        assertThat(response.updatedAt()).isEqualTo("2026-08-03T09:10:20.432Z");
    }

    @Test
    void shouldThrowExceptionWhenBalanceDoesNotExist() {
        UUID accountId = UUID.randomUUID();

        when(balanceRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance(accountId))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(accountId.toString());
    }

    @Test
    void shouldThrowExceptionWhenAccountDoesNotExist () {
        UUID accountId = UUID.randomUUID();

        when(balanceRepository.findById(accountId))
                .thenReturn(Optional.of(new Balance(accountId, BigDecimal.TEN, "BRL", Instant.now())));
        when(accountRepository.findById(accountId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getBalance(accountId))
                .isInstanceOf(AccountNotFoundException.class);
    }


}
