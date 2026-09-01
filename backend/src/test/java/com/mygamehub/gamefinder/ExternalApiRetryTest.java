package com.mygamehub.gamefinder;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
class ExternalApiRetryTest {
    @Test void retriesTransientFailureWithBackoff(){var attempts=new AtomicInteger();var sleeps=new AtomicInteger();var retry=new ExternalApiRetry(ms->sleeps.incrementAndGet());String result=retry.execute(()->{if(attempts.incrementAndGet()<3)throw HttpServerErrorException.create(HttpStatus.SERVICE_UNAVAILABLE,"down",HttpHeaders.EMPTY,null,null);return "ok";});assertThat(result).isEqualTo("ok");assertThat(attempts).hasValue(3);assertThat(sleeps).hasValue(2);}
    @Test void respectsRetryAfterSeconds(){var attempts=new AtomicInteger();var slept=new AtomicLong();var retry=new ExternalApiRetry(slept::set);var headers=new HttpHeaders();headers.set(HttpHeaders.RETRY_AFTER,"2");String result=retry.execute(()->{if(attempts.incrementAndGet()==1)throw org.springframework.web.client.HttpClientErrorException.create(HttpStatus.TOO_MANY_REQUESTS,"limited",headers,null,null);return "ok";});assertThat(result).isEqualTo("ok");assertThat(slept).hasValue(2000);}
}
