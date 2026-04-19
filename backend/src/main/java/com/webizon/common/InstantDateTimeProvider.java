package com.webizon.common;

import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.TemporalAccessor;
import java.util.Optional;

/**
 * Supplies {@link Instant} timestamps to JPA auditing so we never accidentally
 * pick up the JVM default time zone. UTC is the only timestamp we store.
 */
@Component("instantDateTimeProvider")
public class InstantDateTimeProvider implements DateTimeProvider {

    @Override
    public Optional<TemporalAccessor> getNow() {
        return Optional.of(Instant.now());
    }
}
