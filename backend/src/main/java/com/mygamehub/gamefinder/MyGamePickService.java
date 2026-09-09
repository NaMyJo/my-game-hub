package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.MyGamePickResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MyGamePickService {
    private final MyGamePickRepository picks;
    private final SteamGameRepository games;
    private final SteamGameTagRepository tags;

    public MyGamePickService(MyGamePickRepository picks, SteamGameRepository games,
            SteamGameTagRepository tags) {
        this.picks = picks;
        this.games = games;
        this.tags = tags;
    }

    @Transactional(readOnly = true)
    public List<MyGamePickResponse> list(String uid) {
        List<MyGamePick> saved = picks.findAllByFirebaseUidOrderByCreatedAtDesc(uid);
        if (saved.isEmpty()) return List.of();
        List<Long> appIds = saved.stream().map(MyGamePick::getSteamAppId).toList();
        Map<Long, SteamGame> gameById = games.findBySteamAppIdIn(appIds).stream()
                .collect(Collectors.toMap(SteamGame::getSteamAppId, Function.identity()));
        Map<Long, List<String>> tagsById = new HashMap<>();
        tags.findCanonicalNamesBySteamAppIds(appIds).forEach(value ->
                tagsById.computeIfAbsent(value.getSteamAppId(), ignored -> new ArrayList<>())
                        .add(value.getCanonicalName()));
        return saved.stream().filter(pick -> gameById.containsKey(pick.getSteamAppId()))
                .map(pick -> response(gameById.get(pick.getSteamAppId()),
                        tagsById.getOrDefault(pick.getSteamAppId(), List.of()), pick.getCreatedAt()))
                .toList();
    }

    @Transactional
    public MyGamePickResponse add(String uid, long appId) {
        SteamGame game = games.findBySteamAppId(appId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Steam 게임을 찾을 수 없습니다."));
        if (!picks.existsByFirebaseUidAndSteamAppId(uid, appId)) {
            picks.save(new MyGamePick(uid, appId, Instant.now()));
        }
        Instant pickedAt = picks.findByFirebaseUidAndSteamAppId(uid, appId)
                .map(MyGamePick::getCreatedAt).orElse(Instant.now());
        return response(game, tags.findCanonicalNamesBySteamAppId(appId), pickedAt);
    }

    @Transactional
    public void remove(String uid, long appId) {
        picks.deleteByFirebaseUidAndSteamAppId(uid, appId);
    }

    private MyGamePickResponse response(SteamGame game, List<String> canonicalTags,
            Instant pickedAt) {
        String playerSummary = String.join(" · ", List.of(
                game.getSinglePlayer() == Boolean.TRUE ? "싱글" : "",
                game.getMultiplayer() == Boolean.TRUE ? "멀티" : "",
                game.getOnlineCoop() == Boolean.TRUE ? "온라인 협동" : "",
                game.getMaxPlayers() == null ? "" : "최대 " + game.getMaxPlayers() + "명"
        ).stream().filter(value -> !value.isBlank()).toList());
        return new MyGamePickResponse(game.getSteamAppId(), game.getName(),
                game.getHeaderImageUrl(), game.getPriceCurrent(), game.getPriceOriginal(),
                game.getDiscountPercent(), game.getPriceCurrency(), game.getIsFree(),
                game.getReleaseDate(), game.getReleaseDateText(), game.isComingSoon(),
                game.getSinglePlayer(), game.getMultiplayer(), game.getOnlineCoop(),
                game.getMinPlayers(), game.getMaxPlayers(), playerSummary, canonicalTags,
                "https://store.steampowered.com/app/" + game.getSteamAppId(), pickedAt);
    }
}
