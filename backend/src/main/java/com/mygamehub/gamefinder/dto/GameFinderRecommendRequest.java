package com.mygamehub.gamefinder.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record GameFinderRecommendRequest(
        @Size(max=10) List<Long> likedSteamAppIds,
        @Size(max=10) List<String> preferredTags,
        @Min(0) @Max(100000) int priceMin,
        @Min(0) @Max(100000) int priceMax,
        boolean includeAdult,
        @Min(1) @Max(15) int playerMin,
        @Min(1) @Max(15) int playerMax,
        List<Long> excludeAppIds
) implements com.mygamehub.gamefinder.GameFinderFilterCriteria {
    public GameFinderRecommendRequest {
        likedSteamAppIds = likedSteamAppIds == null ? List.of() : List.copyOf(likedSteamAppIds);
        preferredTags = preferredTags == null ? List.of() : List.copyOf(preferredTags);
        excludeAppIds = excludeAppIds == null ? List.of() : List.copyOf(excludeAppIds);
    }
    @AssertTrue(message = "취향 게임 또는 선호 태그를 하나 이상 선택해주세요.")
    public boolean hasTasteInput() {
        return !likedSteamAppIds.isEmpty() || !preferredTags.isEmpty();
    }
}
