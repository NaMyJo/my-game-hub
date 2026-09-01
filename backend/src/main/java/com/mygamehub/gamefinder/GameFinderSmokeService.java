package com.mygamehub.gamefinder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class GameFinderSmokeService {
    private static final Logger log = LoggerFactory.getLogger(GameFinderSmokeService.class);
    private final SteamStoreDetailClient store;
    private final IgdbEnrichmentClient igdb;
    private final SteamGameRepository games;

    public GameFinderSmokeService(SteamStoreDetailClient store,
            IgdbEnrichmentClient igdb, SteamGameRepository games) {
        this.store = store;
        this.igdb = igdb;
        this.games = games;
    }

    @Transactional
    public List<SmokeResult> run(List<Long> appIds) {
        if (appIds.isEmpty() || appIds.size() > 10) {
            throw new IllegalArgumentException("smoke App ID는 1~10개만 허용됩니다.");
        }
        List<SmokeResult> results = new ArrayList<>();
        boolean igdbMapped = false;
        for (long appId : appIds.stream().distinct().toList()) {
            SteamStoreDetailClient.StoreDetail detail = store.get(appId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Steam Store 상세 응답이 없습니다. appId=" + appId));
            if (detail.steamAppId() != appId || detail.name().isBlank()) {
                throw new IllegalStateException("Steam 응답 식별자가 일치하지 않습니다. appId=" + appId);
            }
            SteamGame game = games.findBySteamAppId(appId)
                    .orElseGet(() -> new SteamGame(appId, detail.name(), 0, 0));
            game.updateCatalog(detail.name(), 0, 0);
            game.updateStoreDetail(detail.type(), detail.image(), detail.description(),
                    detail.free(), detail.currency(), detail.original(), detail.current(),
                    detail.discount(), detail.requiredAge(), detail.adult(),
                    detail.releaseDate(), detail.releaseText(), detail.comingSoon(),
                    detail.earlyAccess(), detail.genres(), detail.categories(),
                    detail.single(), detail.multiplayer(), detail.onlineCoop(),
                    detail.offlineCoop());

            IgdbEnrichmentClient.IgdbData igdbData = null;
            if (igdb.configured()) {
                igdbData = igdb.findBySteamAppId(appId).orElse(null);
                if (igdbData != null) {
                    igdbMapped = true;
                    game.updateIgdb(igdbData.gameId(), igdbData.minPlayers(),
                            igdbData.maxPlayers(), igdbData.onlineMax(),
                            igdbData.coopMax(), igdbData.multiplayer(),
                            igdbData.onlineCoop(), igdbData.offlineCoop());
                }
            }
            games.saveAndFlush(game);
            SteamGame saved = games.findBySteamAppId(appId)
                    .orElseThrow(() -> new IllegalStateException(
                            "DB 저장 검증에 실패했습니다. appId=" + appId));
            SmokeResult result = SmokeResult.from(saved,
                    igdbData == null ? null : igdbData.multiplayerModeCount());
            results.add(result);
            log.info("game_finder_smoke_result {}", result);
        }
        if (igdb.configured() && !igdbMapped) {
            throw new IllegalStateException("지정한 Steam App ID 중 IGDB에 연결된 게임이 없습니다.");
        }
        return results;
    }

    public record SmokeResult(long steamAppId, String name, String storeType,
            String image, String currency, Integer currentPrice,
            Integer originalPrice, Integer discountPercent, Boolean free,
            java.util.Set<String> genres, java.util.Set<String> categories,
            java.time.LocalDate releaseDate, String releaseDateText,
            boolean comingSoon, Integer requiredAge, Long igdbGameId,
            Integer multiplayerModeCount, Integer minPlayers,
            Integer maxPlayers, Integer onlineMaxPlayers,
            Integer onlineCoopMaxPlayers) {
        static SmokeResult from(SteamGame game, Integer modeCount) {
            return new SmokeResult(game.getSteamAppId(), game.getName(),
                    game.getStoreType(), game.getHeaderImageUrl(),
                    game.getPriceCurrency(), game.getPriceCurrent(),
                    game.getPriceOriginal(), game.getDiscountPercent(),
                    game.getIsFree(), game.genreSet(), game.categorySet(),
                    game.getReleaseDate(), game.getReleaseDateText(),
                    game.isComingSoon(), game.getRequiredAge(),
                    game.getIgdbGameId(), modeCount, game.getMinPlayers(),
                    game.getMaxPlayers(), game.getOnlineMaxPlayers(),
                    game.getOnlineCoopMaxPlayers());
        }
    }
}
