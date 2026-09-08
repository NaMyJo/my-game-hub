package com.mygamehub.gamefinder;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "igdb_taxonomy_terms",
        uniqueConstraints = @UniqueConstraint(name = "uk_igdb_taxonomy_source_term",
                columnNames = {"source_type", "igdb_term_id"}),
        indexes = @Index(name = "idx_igdb_taxonomy_source_slug",
                columnList = "source_type,slug"))
public class IgdbTaxonomyTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private IgdbTaxonomySourceType sourceType;
    @Column(name = "igdb_term_id", nullable = false)
    private Long igdbTermId;
    @Column(nullable = false, length = 200)
    private String name;
    @Column(nullable = false, length = 200)
    private String slug;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IgdbTaxonomyTerm() {}

    public IgdbTaxonomyTerm(IgdbTaxonomyValue value) {
        update(value);
    }

    public void update(IgdbTaxonomyValue value) {
        sourceType = value.sourceType();
        igdbTermId = value.igdbTermId();
        name = value.name() == null ? "" : value.name();
        slug = value.slug() == null ? "" : value.slug();
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public IgdbTaxonomySourceType getSourceType() { return sourceType; }
    public Long getIgdbTermId() { return igdbTermId; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public Instant getUpdatedAt() { return updatedAt; }
}
