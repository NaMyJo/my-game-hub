package com.mygamehub.gamefinder;

public interface IgdbIntegrityProjection {
    long getTotalChecked();
    long getSuccessMissingGameId();
    long getNotFoundWithGameId();
    long getInvalidPlayerRange();
    long getDuplicateIgdbMapping();
    long getDuplicateTaxonomyRelation();
}
