package com.mygamehub.gamefinder;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.Optional;

@Component
public class IgdbEnrichmentClient {
    private final RestClient rest; private final String clientId; private final String secret;
    private String token; private Instant tokenExpiresAt=Instant.EPOCH;
    public IgdbEnrichmentClient(RestClient.Builder builder,@Value("${app.game-finder.igdb-client-id:}")String clientId,@Value("${app.game-finder.igdb-client-secret:}")String secret){this.rest=builder.build();this.clientId=clientId;this.secret=secret;}
    public boolean configured(){return !clientId.isBlank()&&!secret.isBlank();}
    public Optional<IgdbData> findBySteamAppId(long appId){if(!configured())return Optional.empty();String bearer=token();
        JsonNode links=post("https://api.igdb.com/v4/external_games","fields game; where category = 1 & uid = \""+appId+"\"; limit 1;",bearer);
        if(links==null||!links.isArray()||links.isEmpty())return Optional.empty();long gameId=links.get(0).path("game").asLong();
        JsonNode games=post("https://api.igdb.com/v4/games","fields multiplayer_modes.*,game_modes.name; where id = "+gameId+"; limit 1;",bearer);
        if(games==null||!games.isArray()||games.isEmpty())return Optional.empty();JsonNode game=games.get(0);int min=Integer.MAX_VALUE,max=0,onlineMax=0,coopMax=0;boolean multi=false,onlineCoop=false,offlineCoop=false;
        for(JsonNode mode:game.path("multiplayer_modes")){multi=true;int online=mode.path("onlinemax").asInt(0),offline=mode.path("offlinemax").asInt(0),oc=mode.path("onlinecoopmax").asInt(0),fc=mode.path("offlinecoopmax").asInt(0);onlineMax=Math.max(onlineMax,online);coopMax=Math.max(coopMax,Math.max(oc,fc));max=Math.max(max,Math.max(online,Math.max(offline,Math.max(oc,fc))));if(oc>0||mode.path("onlinecoop").asBoolean())onlineCoop=true;if(fc>0||mode.path("offlinecoop").asBoolean())offlineCoop=true;if(max>0)min=1;}
        return Optional.of(new IgdbData(gameId,game.path("multiplayer_modes").size(),min==Integer.MAX_VALUE?null:min,max==0?null:max,onlineMax==0?null:onlineMax,coopMax==0?null:coopMax,multi,onlineCoop,offlineCoop));}
    private JsonNode post(String url,String body,String bearer){return rest.post().uri(url).contentType(MediaType.TEXT_PLAIN).header("Client-ID",clientId).header("Authorization","Bearer "+bearer).body(body).retrieve().body(JsonNode.class);}
    private synchronized String token(){if(token!=null&&Instant.now().isBefore(tokenExpiresAt))return token;JsonNode response=rest.post().uri(b->b.scheme("https").host("id.twitch.tv").path("/oauth2/token").queryParam("client_id",clientId).queryParam("client_secret",secret).queryParam("grant_type","client_credentials").build()).retrieve().body(JsonNode.class);if(response==null)throw new IllegalStateException("IGDB access token을 발급받지 못했습니다.");token=response.path("access_token").asText();tokenExpiresAt=Instant.now().plusSeconds(Math.max(60,response.path("expires_in").asLong()-60));return token;}
    public record IgdbData(Long gameId,Integer multiplayerModeCount,Integer minPlayers,Integer maxPlayers,Integer onlineMax,Integer coopMax,Boolean multiplayer,Boolean onlineCoop,Boolean offlineCoop){}
}
