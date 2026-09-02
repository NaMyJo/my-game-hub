package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class SteamStoreRequestPolicyTest {
    @Test
    void initialBuildCanMoveOnWithoutRetryWhileRefreshKeepsConfiguredRetries() {
        var initialAttempts = new AtomicInteger();
        var refreshAttempts = new AtomicInteger();
        var policy = new SteamStoreRequestPolicy(0, 2, 0, 1, 2, millis -> {});

        assertThrows(RuntimeException.class, () -> policy.executeInitial(() -> {
            initialAttempts.incrementAndGet();
            throw new ResourceAccessException("timeout");
        }));
        assertThrows(RuntimeException.class, () -> policy.execute(() -> {
            refreshAttempts.incrementAndGet();
            throw new ResourceAccessException("timeout");
        }));

        assertEquals(1, initialAttempts.get());
        assertEquals(3, refreshAttempts.get());
        assertEquals(4, policy.stats().attempts());
        assertEquals(2, policy.stats().retries());
        assertEquals(4, policy.stats().network());
    }

    @Test
    void retries429AndHonorsRetryAfter() {
        var time = new FakeTime();
        var attempts = new AtomicInteger();
        var attemptTimes = new ArrayList<Long>();
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "2");
        var policy = new SteamStoreRequestPolicy(0, 2, 0, 10, 100,
                60_000, time, time);

        String result = policy.execute(() -> {
            attemptTimes.add(time.nanoTime.get());
            if (attempts.incrementAndGet() == 1) throw HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "limited", headers, null, null);
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
        assertTrue(time.sleeps.contains(2000L));
        assertTrue(attemptTimes.get(1) - attemptTimes.get(0) >= 2_000_000_000L,
                "the retry itself must pass the shared Retry-After deadline");
        assertEquals(1, policy.stats().http429());
        assertEquals(1, policy.stats().retries());
    }

    @Test
    void initialRequestWithoutRetriesStillRegistersGlobal429CooldownForNextWorker() {
        var time = new FakeTime();
        var policy = new SteamStoreRequestPolicy(0, 2, 0, 500, 10_000,
                60_000, time, time);
        var limited = new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);

        assertThrows(HttpClientErrorException.class,
                () -> policy.executeInitial(() -> { throw limited; }));
        long firstFailureAt = time.nanoTime.get();
        var nextWorkerStart = new AtomicLong(-1);

        policy.executeInitial(() -> {
            nextWorkerStart.set(time.nanoTime.get());
            return "ok";
        });

        assertTrue(nextWorkerStart.get() - firstFailureAt >= 60_000_000_000L,
                "a later worker/batch must retain the singleton policy cooldown");
        assertEquals(1, policy.stats().http429());
        assertEquals(0, policy.stats().retries());
    }

    @Test
    void normalRequestsResumeAfterGlobalCooldownExpires() {
        var time = new FakeTime();
        var policy = new SteamStoreRequestPolicy(0, 2, 0, 500, 10_000,
                30_000, time, time);
        assertThrows(HttpClientErrorException.class, () -> policy.executeInitial(() -> {
            throw new HttpClientErrorException(HttpStatus.TOO_MANY_REQUESTS);
        }));

        assertEquals("recovered", policy.executeInitial(() -> "recovered"));
        assertTrue(time.nanoTime.get() >= 30_000_000_000L);
    }

    @Test
    void doesNotRetry403() {
        var attempts = new AtomicInteger();
        var policy = new SteamStoreRequestPolicy(0, 2, 10, 100, millis -> {});

        assertThrows(HttpClientErrorException.class, () -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw new HttpClientErrorException(HttpStatus.FORBIDDEN);
        }));
        assertEquals(1, attempts.get());
    }

    @Test
    void stopsAfterConfiguredRetryCount() {
        var attempts = new AtomicInteger();
        var policy = new SteamStoreRequestPolicy(0, 2, 1, 2, millis -> {});

        assertThrows(HttpServerErrorException.class, () -> policy.execute(() -> {
            attempts.incrementAndGet();
            throw new HttpServerErrorException(HttpStatus.SERVICE_UNAVAILABLE);
        }));
        assertEquals(3, attempts.get());
    }

    private static final class FakeTime implements SteamStoreRequestPolicy.Sleeper,
            java.util.function.LongSupplier {
        private final AtomicLong nanoTime = new AtomicLong();
        private final ArrayList<Long> sleeps = new ArrayList<>();

        @Override public synchronized void sleep(long millis) {
            sleeps.add(millis);
            nanoTime.addAndGet(millis * 1_000_000L);
        }

        @Override public long getAsLong() { return nanoTime.get(); }
    }
}
