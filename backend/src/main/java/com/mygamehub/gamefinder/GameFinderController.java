package com.mygamehub.gamefinder;
import com.mygamehub.gamefinder.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@Validated
@RestController @RequestMapping("/api/game-finder")
public class GameFinderController {
    private final GameFinderRecommendationService service;
    private final GameFinderTagSearchService tagSearch;
    private final GameFinderCatalogQueryService catalogQuery;
    public GameFinderController(GameFinderRecommendationService service,GameFinderTagSearchService tagSearch,GameFinderCatalogQueryService catalogQuery){this.service=service;this.tagSearch=tagSearch;this.catalogQuery=catalogQuery;}
    @GetMapping("/search") public List<GameFinderSearchResponse> search(@RequestParam String q){return service.search(q);}
    @PostMapping("/recommend") public List<GameFinderRecommendationResponse> recommend(@Valid @RequestBody GameFinderRecommendRequest body){return service.recommend(body);}
    @PostMapping("/search") public List<GameFinderTagSearchResponse> tagSearch(@Valid @RequestBody GameFinderTagSearchRequest body){return tagSearch.search(body);}

    @GetMapping("/v1/games/search")
    public GameFinderPageResponse<GameFinderSearchResponse> pagedSearch(
            @RequestParam String q,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.searchPage(q,page,size);
    }

    @PostMapping("/v1/games/tag-search")
    public GameFinderPageResponse<GameFinderTagSearchResponse> pagedTagSearch(
            @Valid @RequestBody GameFinderTagSearchRequest body) {
        return tagSearch.searchPage(body);
    }

    @PostMapping("/v1/recommendations")
    public GameFinderPageResponse<GameFinderRecommendationResponse> pagedRecommendations(
            @Valid @RequestBody GameFinderRecommendRequest body,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return service.recommendPage(body,page,size);
    }

    @GetMapping("/v1/games/{steamAppId}")
    public ResponseEntity<GameFinderGameResponse> detail(@PathVariable long steamAppId) {
        return catalogQuery.detail(steamAppId).map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/v1/tags")
    public GameFinderPageResponse<GameFinderTagResponse> tags(
            @RequestParam(defaultValue="") String q,
            @RequestParam(defaultValue="0") @Min(0) int page,
            @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return catalogQuery.autocomplete(q,page,size);
    }
}
