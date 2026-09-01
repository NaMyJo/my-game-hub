package com.mygamehub.gamefinder.dto;
import com.mygamehub.gamefinder.GameFinderFilterCriteria;
import jakarta.validation.constraints.*;
import java.util.List;
public record GameFinderTagSearchRequest(String query,List<String> tags,@Min(0) @Max(100000) int priceMin,@Min(0) @Max(100000) int priceMax,boolean includeAdult,@Min(1) @Max(15) int playerMin,@Min(1) @Max(15) int playerMax,@Min(0) int page,@Min(1) @Max(100) int size) implements GameFinderFilterCriteria{}
