package com.santander.ingestor.service;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class TimestampConverterTest {

    @Test
    void shouldConvertMicrosecondsToInstant() {
        Instant expected = Instant.ofEpochSecond(1_700_000_000L, 250_000_000L);

        Instant actual = TimestampConverter.fromMicros(1_700_000_250_000_000L);

        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void shouldPreservePrecisionWhenConvertingMicroseconds() {
        assertThat(TimestampConverter.fromMicros(2_500_000L))
                .isEqualTo(Instant.ofEpochSecond(2L, 500_000_000L));
    }

    @Test
    void shouldConvertNegativeMicrosecondsCorrectly() {
        assertThat(TimestampConverter.fromMicros(-1L))
                .isEqualTo(Instant.ofEpochSecond(-1L, 999_999_000L));
    }

    @Test
    void shouldParseEpochSecondsStringToInstant() {
        assertThat(TimestampConverter.fromSeconds("1554890321"))
                .isEqualTo(Instant.ofEpochSecond(1_554_890_321L));
    }

}
