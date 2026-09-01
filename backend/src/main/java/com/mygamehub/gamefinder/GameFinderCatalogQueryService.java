package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderGameResponse;
import com.mygamehub.gamefinder.dto.GameFinderPageResponse;
import com.mygamehub.gamefinder.dto.GameFinderTagResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GameFinderCatalogQueryService {
    private final SteamGameRepository games;
    private final SteamGameTagRepository relations;
    private final GameTagRepository tags;
    private final GameTagTaxonomy taxonomy;

    public GameFinderCatalogQueryService(
            SteamGameRepository games,
            SteamGameTagRepository relations,
            GameTagRepository tags,
            GameTagTaxonomy taxonomy) {
        this.games = games;
        this.relations = relations;
        this.tags = tags;
        this.taxonomy = taxonomy;
    }

    public Optional<GameFinderGameResponse> detail(long steamAppId) {
        return games.findBySteamAppId(steamAppId)
                .map(game -> response(game, relations.findCanonicalNamesBySteamAppId(steamAppId)));
    }

    public GameFinderPageResponse<GameFinderTagResponse> autocomplete(
            String query, int page, int size) {
        int offset = page * size;
        String normalizedQuery = taxonomy.normalize(query).orElse(query == null ? "" : query.trim());
        List<GameFinderTagResponse> values = tags.autocomplete(
                        normalizedQuery,
                        PageRequest.of(0, offset + size + 1))
                .stream().skip(offset)
                .map(tag -> new GameFinderTagResponse(
                        tag.getCanonicalName(), tag.getDisplayNameKo(), tag.getType()))
                .toList();
        return GameFinderPageResponse.from(values, page, size);
    }

    private GameFinderGameResponse response(SteamGame game, List<String> canonicalTags) {
        return new GameFinderGameResponse(
                game.getSteamAppId(), game.getName(), game.getHeaderImageUrl(),
                game.getShortDescription(), game.getPriceCurrent(), game.getPriceOriginal(),
                game.getDiscountPercent(), game.getPriceCurrency(), game.getIsFree(),
                game.getReleaseDate(), game.getReleaseDateText(), game.isComingSoon(),
                game.getRequiredAge(), game.getSinglePlayer(), game.getMultiplayer(),
                game.getOnlineCoop(), game.getMinPlayers(), game.getMaxPlayers(),
                game.getLifecycleStatus() == null ? "ACTIVE" : game.getLifecycleStatus().name(),
                canonicalTags, "https://store.steampowered.com/app/" + game.getSteamAppId());
    }
}
