package com.santander.ingestor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.santander.ingestor.data.entity.Balance;
import com.santander.ingestor.data.repository.BalanceRepository;
import com.santander.ingestor.data.repository.ProcessedTransactionRepository;
import com.santander.ingestor.message.TransactionMessage;
import com.santander.ingestor.service.IngestionService;
import com.santander.ingestor.consumer.SqsIngestionPipeline;
import com.santander.ingestor.service.ProcessingResult;
import com.santander.ingestor.service.TimestampConverter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@Tag("integration")
@Transactional
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class MessageDecoderTest {

    static final String QUEUE_NAME = "transacoes-financeiras-processadas";

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("accbalance_db")
                    .withUsername("postgres")
                    .withPassword("password");

    @Container
    static final LocalStackContainer LOCALSTACK = new LocalStackContainer(
            DockerImageName.parse("localstack/localstack:3.7.2"))
            .withServices(LocalStackContainer.Service.SQS);

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("aws.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
        registry.add("aws.sqs.region", () -> "sa-east-1");
        registry.add("aws.sqs.access-key", () -> "test");
        registry.add("aws.sqs.secret-key", () -> "test");
        registry.add("ingestor.scheduling.enabled", () -> "false");
    }

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private SqsIngestionPipeline ingestionPipeline;

    @Autowired
    private BalanceRepository balanceRepository;

    @Autowired
    private ProcessedTransactionRepository processedTransactionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SqsClient sqsClient;

    private TransactionMessage buildMessage(
            String type,
            String status,
            BigDecimal transactionAmount,
            BigDecimal balanceAmount,
            long timestampMicros) {

        return buildMessage(
                UUID.randomUUID(),
                type,
                status,
                transactionAmount,
                balanceAmount,
                timestampMicros
        );
    }

    private TransactionMessage buildMessage(
            UUID accountId,
            String type,
            String status,
            BigDecimal transactionAmount,
            BigDecimal balanceAmount,
            long timestampMicros) {

        UUID transactionId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();

        TransactionMessage.Transaction transaction = new TransactionMessage.Transaction(
                        transactionId,
                        type,
                        transactionAmount,
                        "BRL",
                        status,
                        timestampMicros
                );

        TransactionMessage.Account.Balance accountBalance =
                new TransactionMessage.Account.Balance(balanceAmount, "BRL");

        TransactionMessage.Account account =
                new TransactionMessage.Account(
                        accountId,
                        ownerId,
                        String.valueOf(Instant.now().getEpochSecond()),
                        "ENABLED",
                        accountBalance
                );

        return new TransactionMessage(transaction, account);
    }
}
