package com.mygamehub.gamefinder;
import com.mygamehub.gamefinder.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
@RestController @RequestMapping("/api/game-finder")
public class GameFinderController {
    private final GameFinderRecommendationService service;
    public GameFinderController(GameFinderRecommendationService service){this.service=service;}
    @GetMapping("/search") public List<GameFinderSearchResponse> search(@RequestParam String q){return service.search(q);}
    @PostMapping("/recommend") public List<GameFinderRecommendationResponse> recommend(@Valid @RequestBody GameFinderRecommendRequest body){return service.recommend(body);}
}
