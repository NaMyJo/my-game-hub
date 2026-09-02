package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SteamStoreRequestPolicyTest {
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
