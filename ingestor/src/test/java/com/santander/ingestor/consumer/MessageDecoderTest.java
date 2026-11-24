
package com.santander.ingestor.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.santander.ingestor.exception.MalformedMessageException;
import com.santander.ingestor.message.TransactionMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class MessageDecoderTest {

    private ObjectMapper mapper;
    private MessageDecoder messageDecoder;

    @BeforeEach
    void initialize() {
        mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

        messageDecoder = new MessageDecoder(mapper);
    }

    @Test
    void shouldDeserializeValidTransactionMessage() throws Exception {
        String payload = """
                {
                  "transaction": {
                    "id": "a7f3e808-b154-48b5-9f3e-553935cc4543",
                    "type": "CREDIT",
                    "amount": 125.50,
                    "currency": "BRL",
                    "status": "APPROVED",
                    "timestamp": 1751641364589998
                  },
                  "account": {
                    "id": "c2b19c8b-0cc4-4c72-a989-0c2ee15fa975",
                    "owner": "415e3cfe-f4af-4cd2-b298-a449e614349a",
                    "created_at": "1634874339",
                    "status": "ENABLED",
                    "balance": {
                      "amount": 245.75,
                      "currency": "BRL"
                    }
                  }
                }
                """;

        TransactionMessage result = messageDecoder.decode(payload);

        assertThat(result.transaction().id())
                .isEqualTo(UUID.fromString("a7f3e808-b154-48b5-9f3e-553935cc4543"));
        assertThat(result.transaction().amount())
                .isEqualByComparingTo(new BigDecimal("125.50"));
        assertThat(result.transaction().timestamp())
                .isEqualTo(1751641364589998L);
        assertThat(result.account().createdAt())
                .isEqualTo("1634874339");
        assertThat(result.account().balance().amount())
                .isEqualByComparingTo(new BigDecimal("245.75"));
        assertThat(result.isApproved()).isTrue();
    }

    @Test
    void shouldDeserializePayloadWithAdditionalFields() throws Exception {
        String payload = """
                {
                  "transaction": {
                    "id": "%s",
                    "type": "DEBIT",
                    "amount": 25,
                    "currency": "BRL",
                    "status": "REJECTED",
                    "timestamp": 2,
                    "extra_field": "ignored"
                  },
                  "account": {
                    "id": "%s",
                    "owner": "%s",
                    "created_at": "1634874339",
                    "status": "ENABLED",
                    "balance": {
                      "amount": 25,
                      "currency": "BRL"
                    },
                    "additional_field": true
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID()
        );

        TransactionMessage result = messageDecoder.decode(payload);

        assertThat(result.isApproved()).isFalse();
        assertThat(result.transaction().status()).isEqualTo("REJECTED");
    }

    @Test
    void shouldThrowExceptionWhenJsonIsMalformed() {
        assertThatThrownBy(() -> messageDecoder.decode("{\"transaction\": "))
                .isInstanceOf(MalformedMessageException.class);
    }

    @Test
    void shouldThrowExceptionWhenPayloadIsNull() {
        assertThatThrownBy(() -> messageDecoder.decode(null))
                .isInstanceOf(MalformedMessageException.class);
    }
}
