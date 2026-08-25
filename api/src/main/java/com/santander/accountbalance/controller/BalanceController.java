package com.santander.accountbalance.controller;

import com.santander.accountbalance.dto.BalanceResponse;
import com.santander.accountbalance.service.BalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/balances")
public class BalanceController {

    private final BalanceService balanceService;

    @GetMapping("/{accountId}")
    @Operation(summary = "Consulta o Saldo Mais Recente de Uma Conta",
    description = "Retorna ID da conta, nome do Titular, saldo, moeda e data/hora da ultima atualização.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Saldo encontrado",
                content = @Content(schema = @Schema(implementation = BalanceResponse.class))),
            @ApiResponse(responseCode = "400", description = "accountId inválido"),
            @ApiResponse(responseCode = "404", description = "Conta não encontrada")
    })
    public ResponseEntity<BalanceResponse> getBalance(
            @Parameter(description = "Identificador da conta (UUID)", required = true)
            @PathVariable UUID accountId) {

        return ResponseEntity.ok(balanceService.getBalance(accountId));
    }
}
