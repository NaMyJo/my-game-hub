package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;

@Component
public class SteamStoreDetailClient {
    private final RestClient client; private final ExternalApiRetry retry; private final long requestDelayMs;
    @Autowired
    public SteamStoreDetailClient(RestClient.Builder builder,ExternalApiRetry retry,
            @Value("${app.game-finder.steam-store-request-delay-ms:500}") long requestDelayMs,
            @Value("${app.game-finder.steam-store-connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${app.game-finder.steam-store-read-timeout-ms:10000}") long readTimeoutMs){
        var httpClient=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(connectTimeoutMs)).build();
        var requestFactory=new JdkClientHttpRequestFactory(httpClient);requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        client=builder.requestFactory(requestFactory).baseUrl("https://store.steampowered.com").build();this.retry=retry;this.requestDelayMs=requestDelayMs;
    }
    SteamStoreDetailClient(RestClient.Builder builder,ExternalApiRetry retry){client=builder.baseUrl("https://store.steampowered.com").build();this.retry=retry;this.requestDelayMs=0;}
    public Optional<StoreDetail> get(long appId){
        pause();
        JsonNode root=retry.execute(()->client.get().uri(b->b.path("/api/appdetails").queryParam("appids",appId).queryParam("cc","kr").queryParam("l","korean").build()).retrieve().body(JsonNode.class));
        JsonNode wrapper=root==null?null:root.path(Long.toString(appId)); if(wrapper==null||!wrapper.path("success").asBoolean())return Optional.empty();
        JsonNode d=wrapper.path("data"); Set<String> genres=names(d.path("genres")); Set<String> categories=names(d.path("categories")); Set<Integer> categoryIds=ids(d.path("categories"));
        JsonNode price=d.path("price_overview"); boolean free=d.path("is_free").asBoolean(false); String currency=price.path("currency").asText(null); Integer current=free?Integer.valueOf(0):priceAmount(price,"final",currency); Integer original=free?Integer.valueOf(0):priceAmount(price,"initial",currency);
        String raw=d.path("release_date").path("date").asText(null); boolean coming=d.path("release_date").path("coming_soon").asBoolean(false);
        int age=parseAge(d.path("required_age")); String adult=age>=18?"ADULT":age==0?"NON_ADULT":"UNKNOWN";
        return Optional.of(new StoreDetail(d.path("steam_appid").asLong(appId),d.path("name").asText(),d.path("type").asText(),d.path("header_image").asText(null),d.path("short_description").asText(null),free,
                currency,original,current,number(price,"discount_percent"),age,adult,parseDate(raw),raw,coming,
                d.path("genres").findValuesAsText("id").contains("70"),genres,categories,
                categoryIds.contains(2),categoryIds.contains(1)||categoryIds.contains(9)||categoryIds.contains(20),categoryIds.contains(38),categoryIds.contains(39)));
    }
    private void pause(){if(requestDelayMs<=0)return;try{Thread.sleep(requestDelayMs);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("Steam Store 요청 대기가 중단되었습니다.",e);}}
    private Set<String> names(JsonNode node){Set<String> out=new LinkedHashSet<>();for(JsonNode value:node)out.add(value.path("description").asText());return out;}
    private Set<Integer> ids(JsonNode node){Set<Integer> out=new LinkedHashSet<>();for(JsonNode value:node)out.add(value.path("id").asInt());return out;}
    private Integer number(JsonNode n,String k){return n.has(k)&&n.get(k).isNumber()?n.get(k).asInt():null;}
    private Integer priceAmount(JsonNode node,String key,String currency){Integer raw=number(node,key);return raw==null?null:("KRW".equals(currency)?raw/100:raw);}
    private int parseAge(JsonNode n){try{return Integer.parseInt(n.asText("-1"));}catch(Exception e){return -1;}}
    private LocalDate parseDate(String raw){if(raw==null||raw.isBlank())return null; for(String p:List.of("yyyy년 M월 d일","d MMM, yyyy","MMM d, yyyy")){try{return LocalDate.parse(raw,DateTimeFormatter.ofPattern(p,Locale.ENGLISH));}catch(DateTimeParseException ignored){}} return null;}
    public record StoreDetail(long steamAppId,String name,String type,String image,String description,Boolean free,String currency,Integer original,Integer current,Integer discount,Integer requiredAge,String adult,LocalDate releaseDate,String releaseText,boolean comingSoon,Boolean earlyAccess,Set<String> genres,Set<String> categories,Boolean single,Boolean multiplayer,Boolean onlineCoop,Boolean offlineCoop){}
}
