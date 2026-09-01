package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.*;

@Component
public class SteamCatalogClient {
    private final RestClient client; private final String apiKey; private final int pageSize; private final ExternalApiRetry retry;
    public SteamCatalogClient(RestClient.Builder builder,
            @Value("${app.game-finder.steam-api-key:}") String apiKey,
            @Value("${app.game-finder.catalog-page-size:5000}") int pageSize, ExternalApiRetry retry) {
        this.client=builder.baseUrl("https://partner.steam-api.com").build(); this.apiKey=apiKey; this.pageSize=Math.min(pageSize,50000); this.retry=retry;
    }
    public CatalogPage page(long lastAppId, Long modifiedSince) {
        if(apiKey.isBlank()) throw new IllegalStateException("STEAM_WEB_API_KEY 환경변수가 설정되지 않았습니다.");
        JsonNode root=retry.execute(()->client.get().uri(builder->{builder.path("/IStoreService/GetAppList/v1/")
                .queryParam("key",apiKey).queryParam("include_games",true).queryParam("include_dlc",false)
                .queryParam("include_software",false).queryParam("include_videos",false)
                .queryParam("include_hardware",false).queryParam("last_appid",lastAppId)
                .queryParam("max_results",pageSize); if(modifiedSince!=null&&modifiedSince>0)builder.queryParam("if_modified_since",modifiedSince); return builder.build();})
                .retrieve().body(JsonNode.class));
        JsonNode response=root==null?null:root.path("response"); List<CatalogItem> items=new ArrayList<>();
        if(response!=null) for(JsonNode app:response.path("apps")) items.add(new CatalogItem(app.path("appid").asLong(),app.path("name").asText(),app.path("last_modified").asLong(),app.path("price_change_number").asLong()));
        return new CatalogPage(items,response!=null&&response.path("have_more_results").asBoolean(false),response==null?lastAppId:response.path("last_appid").asLong(lastAppId));
    }
    public record CatalogItem(long appId,String name,long lastModified,long priceChangeNumber){}
    public record CatalogPage(List<CatalogItem> items,boolean hasMore,long lastAppId){}
}
