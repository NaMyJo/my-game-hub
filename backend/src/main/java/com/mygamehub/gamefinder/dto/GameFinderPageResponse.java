package com.mygamehub.gamefinder.dto;

import java.util.List;

public record GameFinderPageResponse<T>(
        List<T> items,
        int page,
        int size,
        boolean hasNext
) {
    public static <T> GameFinderPageResponse<T> from(List<T> candidates, int page, int size) {
        boolean hasNext = candidates.size() > size;
        List<T> items = hasNext ? List.copyOf(candidates.subList(0, size)) : List.copyOf(candidates);
        return new GameFinderPageResponse<>(items, page, size, hasNext);
    }
}
