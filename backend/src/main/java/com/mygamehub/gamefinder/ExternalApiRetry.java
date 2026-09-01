package com.mygamehub.gamefinder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import java.util.function.Supplier;
@Component
public class ExternalApiRetry {
    private final Sleeper sleeper;
    public ExternalApiRetry(){this(Thread::sleep);}
    ExternalApiRetry(Sleeper sleeper){this.sleeper=sleeper;}
    public <T>T execute(Supplier<T> action){RuntimeException last=null;for(int attempt=0;attempt<3;attempt++){try{return action.get();}catch(HttpClientErrorException.TooManyRequests|HttpServerErrorException|ResourceAccessException e){last=e;if(attempt<2)pause(250L*(1L<<attempt));}}throw last;}
    private void pause(long millis){try{sleeper.sleep(millis);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("외부 API 재시도가 중단되었습니다.",e);}}
    interface Sleeper{void sleep(long millis)throws InterruptedException;}
}
