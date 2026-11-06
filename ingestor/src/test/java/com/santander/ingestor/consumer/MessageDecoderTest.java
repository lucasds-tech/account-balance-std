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

    @Test
    void shouldApplyBalanceFromApprovedTransaction() {
        TransactionMessage message = buildMessage("CREDIT", "APPROVED",
                new BigDecimal("125.50"), new BigDecimal("325.75"),
                1_700_000_250_000_000L);

        ProcessingResult result = ingestionService.process(message, "message-001");

        assertThat(result).isEqualTo(ProcessingResult.APPLIED);

        Balance balance = balanceRepository.findById(message.account().id()).orElseThrow();

        assertThat(balance.getAmount()).isEqualByComparingTo(new BigDecimal("325.75"));
        assertThat(balance.getCurrency()).isEqualTo("BRL");
        assertThat(balance.getUpdatedAt())
                .isEqualTo(TimestampConverter.fromMicros(1_700_000_250_000_000L));
    }

    @Test
    void shouldIgnorePreviouslyProcessedMessage() {
        TransactionMessage message = buildMessage("CREDIT", "APPROVED",
                new BigDecimal("25.00"), new BigDecimal("75.00"),
                1_700_000_300_000_000L);

        assertThat(ingestionService.process(message, "message-002"))
                .isEqualTo(ProcessingResult.APPLIED);

        assertThat(ingestionService.process(message, "message-002"))
                .isEqualTo(ProcessingResult.DUPLICATE);

        assertThat(balanceRepository.findById(message.account().id()).orElseThrow().getAmount())
                .isEqualByComparingTo(new BigDecimal("75.00"));
    }

    @Test
    void shouldPreserveLatestBalanceWhenReceivingOlderMessage() {
        UUID accountId = UUID.randomUUID();

        TransactionMessage latestMessage = buildMessage(accountId, "CREDIT", "APPROVED",
                new BigDecimal("30.00"), new BigDecimal("150.00"),
                1_700_000_400_000_000L);

        assertThat(ingestionService.process(latestMessage, "message-latest"))
                .isEqualTo(ProcessingResult.APPLIED);

        TransactionMessage olderMessage = buildMessage(accountId, "DEBIT", "APPROVED",
                new BigDecimal("15.00"), new BigDecimal("135.00"),
                1_700_000_200_000_000L);

        assertThat(ingestionService.process(olderMessage, "message-older"))
                .isEqualTo(ProcessingResult.STALE);

        Balance balance = balanceRepository.findById(accountId).orElseThrow();

        assertThat(balance.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(balance.getUpdatedAt())
                .isEqualTo(TimestampConverter.fromMicros(1_700_000_400_000_000L));
    }

    @Test
    void shouldNotCreateBalanceForUnapprovedTransaction() {
        TransactionMessage message = buildMessage("DEBIT", "REJECTED",
                new BigDecimal("200.00"), new BigDecimal("0.00"),
                1_700_000_500_000_000L);

        assertThat(ingestionService.process(message, "message-rejected"))
                .isEqualTo(ProcessingResult.NOT_APPROVED);

        assertThat(processedTransactionRepository.existsById(message.transaction().id()))
                .isTrue();

        assertThat(balanceRepository.findById(message.account().id()))
                .isEmpty();
    }

    @Test
    void shouldReceiveSqsMessageAndUpdateAccountBalance() throws Exception {
        String queueUrl = sqsClient.createQueue(r -> r.queueName(QUEUE_NAME)).queueUrl();

        TransactionMessage message = buildMessage("CREDIT", "APPROVED",
                new BigDecimal("125.50"), new BigDecimal("325.75"),
                1_700_000_600_000_000L);

        String messageBody = objectMapper.writeValueAsString(message);

        sqsClient.sendMessage(r -> r.queueUrl(queueUrl)
                .messageBody(messageBody));

        ingestionPipeline.consume();

        Optional<Balance> storedBalance = balanceRepository.findById(message.account().id());

        assertThat(storedBalance).isPresent();
        assertThat(storedBalance.orElseThrow().getAmount())
                .isEqualByComparingTo(new BigDecimal("325.75"));

        long remainingMessages = sqsClient.getQueueAttributes(r -> r
                        .queueUrl(queueUrl)
                        .attributeNames(software.amazon.awssdk.services.sqs.model.QueueAttributeName
                                .APPROXIMATE_NUMBER_OF_MESSAGES))
                .attributes()
                .values()
                .stream()
                .mapToLong(Long::parseLong)
                .sum();

        assertThat(remainingMessages).isZero();
    }

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
