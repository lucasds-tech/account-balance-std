package com.santander.ingestor.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class IngestionMetrics {

    private final Counter applied;
    private final Counter duplicate;
    private final Counter stale;
    private final Counter notApproved;
    private final Counter malformed;

    public IngestionMetrics(MeterRegistry registry) {
        this.applied = Counter.builder("ingestor.messages.applied")
                .description("Total de mensagens processadas com sucesso, com atualização do saldo da conta")
                .register(registry);
        this.duplicate = Counter.builder("ingestor.messages.duplicate")
                .description("Total de mensagens duplicadas identificadas e ignoradas pelo mecanismo de idempotência")
                .register(registry);
        this.stale = Counter.builder("ingestor.messages.stale")
                .description("Total de mensagens descartadas por conterem transações anteriores à última atualização do saldo")
                .register(registry);
        this.notApproved = Counter.builder("ingestor.messages.not_approved")
                .description("Total de mensagens referentes a transações não aprovadas, que não alteram o saldo da conta")
                .register(registry);
        this.malformed = Counter.builder("ingestor.messages.malformed")
                .description("Total de mensagens com payload inválido ou malformado, encaminhadas para a Dead Letter Queue (DLQ)")
                .register(registry);
    }

    public void applied() {
        applied.increment();
    }

    public void duplicate() {
        duplicate.increment();

    }
    public void stale() {
        stale.increment();
    }

    public void notApproved() {
        notApproved.increment();
    }

    public void malformed() {
        malformed.increment();
    }
}
