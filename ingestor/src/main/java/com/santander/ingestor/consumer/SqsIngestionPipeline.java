package com.santander.ingestor.consumer;

import com.santander.ingestor.exception.MalformedMessageException;
import com.santander.ingestor.message.TransactionMessage;
import com.santander.ingestor.service.IngestionService;
import com.santander.ingestor.service.ProcessingResult;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsIngestionPipeline {

    private final SqsClient sqsClient;
    private final SqsQueue sqsQueue;
    private final MessageDecoder messageDecoder;
    private final IngestionService ingestionService;

    @Value("${ingestor.batch-size:10}")
    private int batchSize;

    @Value("${ingestor.wait-time-seconds:5}")
    private int waitTimeSeconds;

    @Value("${ingestor.visibility-timeout:30}")
    private int visibilityTimeout;


    private List<Message> receive() {
        ReceiveMessageResponse response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(sqsQueue.url())
                .maxNumberOfMessages(batchSize)
                .waitTimeSeconds(waitTimeSeconds)
                .visibilityTimeout(visibilityTimeout)
                .build());
        return response.messages();
    }

    @Retry(name = "sqsConsumer")
    @CircuitBreaker(name = "sqsConsumer")
    public void consume() {
        List<Message> messages = receive();

        if (messages.isEmpty()) {
            return;
        }

        List<String> confirmedHandles = processMessages(messages);

        if (!confirmedHandles.isEmpty()) {
            delete(confirmedHandles);
        }

        log.info("Processamento do ciclo finalizado: mensagens recebidas={}, mensagens confirmadas={}",
                messages.size(), confirmedHandles.size());
    }

    private List<String> processMessages(List<Message> messages) {
        List<String> confirmedHandles = new ArrayList<>(messages.size());

        for (Message message : messages) {
            try {
                TransactionMessage payload = messageDecoder.decode(message.body());
                ProcessingResult result = ingestionService.process(payload, message.messageId());
                log.info("Mensagem processada: messageId={}, transactionId={}, processingResult={}",
                        message.messageId(), payload.transaction().id(), result);
                confirmedHandles.add(message.receiptHandle());
            } catch (MalformedMessageException e) {
                log.warn("Falha ao processar mensagem: messageId={}, payload inválido. Mensagem não confirmada e sujeita à DLQ após atingir o limite de tentativas. Motivo={}",
                        message.messageId(), e.getMessage());
            }
        }
        return confirmedHandles;
    }

    private void delete(List<String> receiptHandles) {
        List<DeleteMessageBatchRequestEntry> entries = receiptHandles.stream()
                .map(handle -> DeleteMessageBatchRequestEntry.builder()
                        .id(UUID.randomUUID().toString())
                        .receiptHandle(handle)
                        .build())
                .toList();

        sqsClient.deleteMessageBatch(DeleteMessageBatchRequest.builder()
                .queueUrl(sqsQueue.url())
                .entries(entries)
                .build());
    }

}
