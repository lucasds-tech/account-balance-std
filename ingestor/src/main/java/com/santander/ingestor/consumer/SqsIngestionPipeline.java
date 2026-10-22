package com.santander.ingestor.consumer;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SqsIngestionPipeline {

    private final SqsClient sqsClient;
    private final SqsQueue sqsQueue;
    private final MessageDecoder messageDecoder;

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

}
