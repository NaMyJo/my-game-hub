package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** Bounded retry and globally spaced request starts for the Store endpoint. */
@Component
public class SteamStoreRequestPolicy {
    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final long requestDelayMs;
    private final long requestDelayNanos;
    private final Sleeper sleeper;
    private long nextRequestNanos;

    public SteamStoreRequestPolicy(
            @Value("${app.game-finder.steam-store-request-delay-ms:500}") long requestDelayMs,
            @Value("${app.game-finder.steam-store-max-retries:2}") int maxRetries,
            @Value("${app.game-finder.steam-store-backoff-initial-ms:500}") long initialBackoffMs,
            @Value("${app.game-finder.steam-store-backoff-max-ms:10000}") long maxBackoffMs) {
        this(requestDelayMs, maxRetries, initialBackoffMs, maxBackoffMs, Thread::sleep);
    }

    SteamStoreRequestPolicy(long requestDelayMs, int maxRetries, long initialBackoffMs,
            long maxBackoffMs, Sleeper sleeper) {
        this.requestDelayMs = Math.max(0, requestDelayMs);
        this.requestDelayNanos = Duration.ofMillis(this.requestDelayMs).toNanos();
        this.maxRetries = Math.max(0, maxRetries);
        this.initialBackoffMs = Math.max(1, initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
        this.sleeper = sleeper;
    }

    public <T> T execute(Supplier<T> action) {
        RuntimeException last = null;
        // Preserve the existing single-worker delay semantics. With multiple workers,
        // awaitRequestSlot additionally prevents a burst after their waits overlap.
        sleep(requestDelayMs);
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                awaitRequestSlot();
                return action.get();
            } catch (RuntimeException exception) {
                if (!retryable(exception) || attempt == maxRetries) throw exception;
                last = exception;
                sleep(retryDelayMs(exception, attempt));
            }
        }
        throw last;
    }

    private boolean retryable(RuntimeException exception) {
        if (exception instanceof HttpClientErrorException error) {
            return error.getStatusCode().value() == 429;
        }
        return exception instanceof HttpServerErrorException
                || exception instanceof ResourceAccessException
                || exception instanceof RestClientException;
    }

    private long retryDelayMs(RuntimeException exception, int retryIndex) {
        Long retryAfter = retryAfterMillis(exception);
        if (retryAfter != null) return retryAfter;
        long exponential = Math.min(maxBackoffMs,
                initialBackoffMs * (1L << Math.min(retryIndex, 20)));
        long jitterBound = Math.max(1, exponential / 5);
        return Math.min(maxBackoffMs, exponential
                + ThreadLocalRandom.current().nextLong(jitterBound));
    }

    private Long retryAfterMillis(RuntimeException exception) {
        if (!(exception instanceof RestClientResponseException response)
                || response.getResponseHeaders() == null) return null;
        return parseRetryAfter(response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER));
    }

    static Long parseRetryAfter(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Math.max(0, Long.parseLong(value.trim()) * 1000L);
        } catch (NumberFormatException ignored) {
            try {
                return Math.max(0, Duration.between(Instant.now(), ZonedDateTime.parse(
                        value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());
            } catch (Exception ignoredDate) {
                return null;
            }
        }
    }

    private synchronized void awaitRequestSlot() {
        long waitNanos = Math.max(0, nextRequestNanos - System.nanoTime());
        if (waitNanos > 0) sleep(Duration.ofNanos(waitNanos).toMillis() + 1);
        nextRequestNanos = System.nanoTime() + requestDelayNanos;
    }

    private void sleep(long millis) {
        try {
            sleeper.sleep(Math.max(0, millis));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Steam Store request wait was interrupted", exception);
        }
    }

    interface Sleeper { void sleep(long millis) throws InterruptedException; }
}
