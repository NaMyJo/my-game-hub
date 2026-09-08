package com.mygamehub.gamefinder;

public record IgdbTaxonomyValue(
        IgdbTaxonomySourceType sourceType,
        long igdbTermId,
        String name,
        String slug) {
}
