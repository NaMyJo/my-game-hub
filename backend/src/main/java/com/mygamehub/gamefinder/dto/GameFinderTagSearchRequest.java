package com.mygamehub.gamefinder.dto;
import com.mygamehub.gamefinder.GameFinderFilterCriteria;
import jakarta.validation.constraints.*;
import java.util.List;
public record GameFinderTagSearchRequest(String query,List<String> tags,
        @Min(0) @Max(100000) int priceMin,@Min(0) @Max(100000) int priceMax,
        boolean includeAdult,@Min(1) @Max(15) int playerMin,@Min(1) @Max(15) int playerMax,
        @Min(0) int page,@Min(1) @Max(100) int size,
        com.mygamehub.gamefinder.PlayMode playMode,
        com.mygamehub.gamefinder.PriceMode priceMode) implements GameFinderFilterCriteria {
    public GameFinderTagSearchRequest(String query,List<String> tags,int priceMin,int priceMax,
            boolean includeAdult,int playerMin,int playerMax,int page,int size) {
        this(query,tags,priceMin,priceMax,includeAdult,playerMin,playerMax,page,size,null,null);
    }
}
