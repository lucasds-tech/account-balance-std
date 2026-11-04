package com.santander.ingestor.service;

import com.santander.ingestor.data.repository.AccountRepository;
import com.santander.ingestor.data.repository.BalanceRepository;
import com.santander.ingestor.data.repository.ProcessedTransactionRepository;
import com.santander.ingestor.exception.MalformedMessageException;
import com.santander.ingestor.message.TransactionMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final AccountRepository accountRepository;
    private final BalanceRepository balanceRepository;
    private final ProcessedTransactionRepository processedTransactionRepository;
    private final IngestionMetrics metrics;

    @Transactional
    public ProcessingResult process(TransactionMessage message, String messageId) {
        validate(message);
        return doProcess(message, messageId);
    }

    private ProcessingResult doProcess(TransactionMessage message, String messageId) {
        TransactionMessage.Transaction tx = message.transaction();
        TransactionMessage.Account acc = message.account();

        if (processedTransactionRepository.insertIfAbsent(tx.id(), Instant.now()) == 0) {
            metrics.duplicate();
            log.debug("Mensagem duplicada ignorada: transactionId={}, messageId={}",
                    tx.id(), messageId);
            return ProcessingResult.DUPLICATE;
        }

        if (!message.isApproved()) {
            metrics.notApproved();
            log.info("Transação não aprovada; saldo mantido inalterado: transactionId={}, status={}, messageId={}",
                    tx.id(), tx.status(), messageId);
            return ProcessingResult.NOT_APPROVED;
        }

        Instant accountCreatedAt = TimestampConverter.fromSeconds(acc.createdAt());
        accountRepository.insertIfAbsent(acc.id(), acc.owner(), acc.status(), accountCreatedAt);

        TransactionMessage.Account.Balance balanceInfo = acc.balance();
        Instant transactionTimestamp = TimestampConverter.fromMicros(tx.timestamp());

        int rows = balanceRepository.upsertIfNewer(
                acc.id(), balanceInfo.amount(), balanceInfo.currency(), transactionTimestamp);

        if (rows == 0) {
            metrics.stale();
            log.info("Transação ignorada por desatualização: transactionId={}, motivo=saldo já atualizado com dados mais recentes",
                    tx.id());
            return ProcessingResult.STALE;
        }

        metrics.applied();
        log.info("Saldo da conta atualizado com sucesso: accountId={}, amount={}, currency={}, transactionId={}",
                acc.id(), balanceInfo.amount(), balanceInfo.currency(), tx.id());
        return ProcessingResult.APPLIED;
    }

    private void validate(TransactionMessage message) {
        TransactionMessage.Transaction tx = message.transaction();
        TransactionMessage.Account acc = message.account();
        TransactionMessage.Account.Balance balance =
                (acc == null) ? null : acc.balance();

        if (tx == null || acc == null || balance == null
                || tx.id() == null || acc.id() == null || acc.owner() == null
                || acc.createdAt() == null || acc.status() == null
                || balance.amount() == null || balance.currency() == null
                || tx.amount() == null || tx.currency() == null) {
            metrics.malformed();
            throw new MalformedMessageException(
                    "Mensagem inválida descartada: payload ausente ou com campos obrigatórios nulos");
        }
    }

}
