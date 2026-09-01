package com.mygamehub.gamefinder.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record GameFinderRecommendRequest(
        @NotEmpty @Size(max=10) List<Long> likedSteamAppIds,
        @Min(0) @Max(100000) int priceMin,
        @Min(0) @Max(100000) int priceMax,
        boolean includeAdult,
        @Min(1) @Max(15) int playerMin,
        @Min(1) @Max(15) int playerMax,
        List<Long> excludeAppIds
) {}
