package com.mygamehub.gamefinder.dto;
import java.util.List;
public record GameFinderPreferenceResponse(List<GameFinderSearchResponse> selectedGames,
 List<String> preferredTags,int priceMin,int priceMax,boolean includeAdult,int playerMin,int playerMax,
 List<GameFinderSearchResponse> recentGames) {}
