package com.mygamehub.gamefinder.dto;
import jakarta.validation.constraints.*;
import java.util.List;
public record GameFinderPreferenceRequest(
 @NotNull @Size(max=10) List<Long> selectedSteamAppIds,
 @NotNull @Size(max=10) List<String> preferredTags,
 @Min(0) @Max(100000) int priceMin,@Min(0) @Max(100000) int priceMax,
 boolean includeAdult,@Min(1) @Max(15) int playerMin,@Min(1) @Max(15) int playerMax) {}
