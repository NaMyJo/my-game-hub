package com.mygamehub.gamefinder.dto;
import java.util.List;
public record GameFinderTagSearchResponse(Long steamAppId,String name,String headerImageUrl,Integer currentPrice,Integer originalPrice,Integer discountPercent,Boolean isFree,Boolean multiplayer,Boolean onlineCoop,Integer minPlayers,Integer maxPlayers,boolean comingSoon,List<String> canonicalTags,String storeUrl){}
