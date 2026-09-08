package com.mygamehub.gamefinder;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;

public interface IgdbTaxonomyTermRepository extends JpaRepository<IgdbTaxonomyTerm, Long> {
    List<IgdbTaxonomyTerm> findBySourceTypeInAndIgdbTermIdIn(
            Collection<IgdbTaxonomySourceType> sourceTypes,
            Collection<Long> igdbTermIds);
}
