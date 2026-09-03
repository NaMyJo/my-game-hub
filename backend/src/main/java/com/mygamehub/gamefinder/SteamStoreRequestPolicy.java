package com.mygamehub.gamefinder;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.concurrent.atomic.LongAdder;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Bounded retry and globally spaced request starts for the Store endpoint. */
@Component
public class SteamStoreRequestPolicy {
    private final int maxRetries;
    private final long initialBackoffMs;
    private final long maxBackoffMs;
    private final long requestDelayMs;
    private final long requestDelayNanos;
    private final int initialMaxRetries;
    private final long rateLimitCooldownMs;
    private final Sleeper sleeper;
    private final LongSupplier nanoTime;
    private long nextRequestNanos;
    private final LongAdder executions = new LongAdder();
    private final LongAdder attempts = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder http429 = new LongAdder();
    private final LongAdder http5xx = new LongAdder();
    private final LongAdder network = new LongAdder();
    private final LongAdder parsing = new LongAdder();

    @Autowired
    public SteamStoreRequestPolicy(
            @Value("${app.game-finder.steam-store-request-delay-ms:500}") long requestDelayMs,
            @Value("${app.game-finder.steam-store-max-retries:2}") int maxRetries,
            @Value("${app.game-finder.steam-store-initial-max-retries:0}") int initialMaxRetries,
            @Value("${app.game-finder.steam-store-backoff-initial-ms:500}") long initialBackoffMs,
            @Value("${app.game-finder.steam-store-backoff-max-ms:10000}") long maxBackoffMs,
            @Value("${app.game-finder.steam-store-rate-limit-cooldown-ms:60000}") long rateLimitCooldownMs) {
        this(requestDelayMs, maxRetries, initialMaxRetries, initialBackoffMs,
                maxBackoffMs, rateLimitCooldownMs, Thread::sleep, System::nanoTime);
    }

    SteamStoreRequestPolicy(long requestDelayMs, int maxRetries, long initialBackoffMs,
            long maxBackoffMs, Sleeper sleeper) {
        this(requestDelayMs, maxRetries, 0, initialBackoffMs, maxBackoffMs, sleeper);
    }

    SteamStoreRequestPolicy(long requestDelayMs, int maxRetries, int initialMaxRetries,
            long initialBackoffMs, long maxBackoffMs, Sleeper sleeper) {
        this(requestDelayMs, maxRetries, initialMaxRetries, initialBackoffMs,
                maxBackoffMs, 60_000, sleeper, System::nanoTime);
    }

    SteamStoreRequestPolicy(long requestDelayMs, int maxRetries, int initialMaxRetries,
            long initialBackoffMs, long maxBackoffMs, long rateLimitCooldownMs,
            Sleeper sleeper, LongSupplier nanoTime) {
        this.requestDelayMs = Math.max(0, requestDelayMs);
        this.requestDelayNanos = Duration.ofMillis(this.requestDelayMs).toNanos();
        this.maxRetries = Math.max(0, maxRetries);
        this.initialMaxRetries = Math.max(0, initialMaxRetries);
        this.initialBackoffMs = Math.max(1, initialBackoffMs);
        this.maxBackoffMs = Math.max(this.initialBackoffMs, maxBackoffMs);
        this.rateLimitCooldownMs = Math.max(1, rateLimitCooldownMs);
        this.sleeper = sleeper;
        this.nanoTime = nanoTime;
    }

    public <T> T execute(Supplier<T> action) {
        return execute(action, maxRetries);
    }

    public <T> T executeInitial(Supplier<T> action) {
        return execute(action, initialMaxRetries);
    }

    private <T> T execute(Supplier<T> action, int retryLimit) {
        RuntimeException last = null;
        executions.increment();
        // Preserve the existing single-worker delay semantics. With multiple workers,
        // awaitRequestSlot additionally prevents a burst after their waits overlap.
        sleep(requestDelayMs);
        for (int attempt = 0; attempt <= retryLimit; attempt++) {
            try {
                awaitRequestSlot();
                attempts.increment();
                return action.get();
            } catch (RuntimeException exception) {
                recordFailure(exception);
                long delay = retryDelayMs(exception, attempt);
                // A 429 is a server-wide signal, not merely a retry decision for this App.
                // Register it even when initialMaxRetries=0.
                if (is429(exception)) deferAllRequests(rateLimitDelayMs(exception, delay));
                if (!retryable(exception) || attempt == retryLimit) throw exception;
                last = exception;
                retries.increment();
                sleep(delay);
            }
        }
        throw last;
    }

    private boolean is429(RuntimeException exception) {
        return exception instanceof HttpClientErrorException error
                && error.getStatusCode().value() == 429;
    }

    private void recordFailure(RuntimeException exception) {
        String category = failureCategory(exception);
        switch (category) {
            case "HTTP_429" -> http429.increment();
            case "HTTP_5XX" -> http5xx.increment();
            case "NETWORK" -> network.increment();
            case "PARSING" -> parsing.increment();
            default -> { }
        }
    }

    static String failureCategory(RuntimeException exception) {
        if (exception instanceof HttpClientErrorException error
                && error.getStatusCode().value() == 429) return "HTTP_429";
        if (exception instanceof HttpServerErrorException) return "HTTP_5XX";
        if (exception instanceof ResourceAccessException) return "NETWORK";
        if (exception instanceof HttpClientErrorException) return "OTHER";
        if (exception instanceof SteamStoreDetailClient.SteamStoreResponseException
                || exception instanceof RestClientException) return "PARSING";
        return "OTHER";
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

    private long rateLimitDelayMs(RuntimeException exception, long retryDelayMs) {
        Long retryAfter = retryAfterMillis(exception);
        return retryAfter != null ? retryAfter : Math.max(rateLimitCooldownMs, retryDelayMs);
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

    private void awaitRequestSlot() {
        while (true) {
            long waitNanos;
            synchronized (this) {
                long now = nanoTime.getAsLong();
                waitNanos = Math.max(0, nextRequestNanos - now);
                if (waitNanos == 0) {
                    nextRequestNanos = now + requestDelayNanos;
                    return;
                }
            }
            // Never hold the monitor while waiting. A concurrent 429 must be able to
            // extend the deadline, and this loop re-checks that extended deadline.
            sleep(Duration.ofNanos(waitNanos).toMillis() + 1);
        }
    }

    private synchronized void deferAllRequests(long delayMs) {
        long deferredUntil = nanoTime.getAsLong()
                + Duration.ofMillis(Math.max(0, delayMs)).toNanos();
        nextRequestNanos = Math.max(nextRequestNanos, deferredUntil);
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

    long requestDelayMs() { return requestDelayMs; }
    int maxRetries() { return maxRetries; }
    long initialBackoffMs() { return initialBackoffMs; }
    long maxBackoffMs() { return maxBackoffMs; }
    int initialMaxRetries() { return initialMaxRetries; }
    long rateLimitCooldownMs() { return rateLimitCooldownMs; }
    long remainingGlobalBackoffMs() {
        synchronized (this) {
            long remaining = Math.max(0, nextRequestNanos - nanoTime.getAsLong());
            return Duration.ofNanos(remaining).toMillis();
        }
    }

    Stats stats() {
        return new Stats(executions.sum(), attempts.sum(), retries.sum(), http429.sum(),
                http5xx.sum(), network.sum(), parsing.sum());
    }

    record Stats(long executions, long attempts, long retries, long http429,
            long http5xx, long network, long parsing) {
        Stats minus(Stats before) {
            return new Stats(executions-before.executions, attempts-before.attempts,
                    retries-before.retries, http429-before.http429, http5xx-before.http5xx,
                    network-before.network, parsing-before.parsing);
        }
        String averageAttemptsPerApp() {
            return executions == 0 ? "0.00"
                    : String.format(java.util.Locale.ROOT, "%.2f", (double) attempts / executions);
        }
    }
}
