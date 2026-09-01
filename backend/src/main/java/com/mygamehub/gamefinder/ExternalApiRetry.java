package com.mygamehub.gamefinder;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;
@Component
public class ExternalApiRetry {
    private final Sleeper sleeper;
    public ExternalApiRetry(){this(Thread::sleep);}
    ExternalApiRetry(Sleeper sleeper){this.sleeper=sleeper;}
    public <T>T execute(Supplier<T> action){RuntimeException last=null;for(int attempt=0;attempt<3;attempt++){try{return action.get();}catch(RuntimeException e){if(!retryable(e))throw e;last=e;if(attempt<2)pause(retryDelay(e,attempt));}}throw last;}
    private boolean retryable(RuntimeException e){return e instanceof HttpClientErrorException.TooManyRequests||e instanceof HttpServerErrorException||e instanceof ResourceAccessException||(e instanceof RetryableFailure failure&&failure.isRetryable());}
    private long retryDelay(RuntimeException e,int attempt){Long retryAfter=retryAfterMillis(e);return retryAfter!=null?retryAfter:250L*(1L<<attempt);}
    private Long retryAfterMillis(RuntimeException e){
        if(e instanceof RetryableFailure failure&&failure.retryAfterMillis()!=null)return failure.retryAfterMillis();
        if(e instanceof RestClientResponseException response){String value=response.getResponseHeaders()==null?null:response.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER);return parseRetryAfter(value);}
        return null;
    }
    static Long parseRetryAfter(String value){if(value==null||value.isBlank())return null;try{return Math.max(0,Long.parseLong(value.trim())*1000L);}catch(NumberFormatException ignored){try{return Math.max(0,Duration.between(Instant.now(),ZonedDateTime.parse(value,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toMillis());}catch(Exception ignoredDate){return null;}}}
    private void pause(long millis){try{sleeper.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("외부 API 재시도가 중단되었습니다.",e);}}
    interface Sleeper{void sleep(long millis)throws InterruptedException;}
    interface RetryableFailure{boolean isRetryable();Long retryAfterMillis();}
}
