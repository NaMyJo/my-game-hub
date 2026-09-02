package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

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
        var sleeps = new ArrayList<Long>();
        var attempts = new AtomicInteger();
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.RETRY_AFTER, "2");
        var policy = new SteamStoreRequestPolicy(0, 2, 10, 100, sleeps::add);

        String result = policy.execute(() -> {
            if (attempts.incrementAndGet() == 1) throw HttpClientErrorException.create(
                    HttpStatus.TOO_MANY_REQUESTS, "limited", headers, null, null);
            return "ok";
        });

        assertEquals("ok", result);
        assertEquals(2, attempts.get());
        assertTrue(sleeps.contains(2000L));
        int waitsAfterLimitedRequest = sleeps.size();

        policy.execute(() -> "next-worker-request");

        assertTrue(sleeps.size() > waitsAfterLimitedRequest,
                "429 backoff must defer the next globally scheduled request too");
        assertEquals(1, policy.stats().http429());
        assertEquals(1, policy.stats().retries());
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
}
