package com.mygamehub.gamefinder;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "steam_game_igdb_terms",
        uniqueConstraints = @UniqueConstraint(name = "uk_steam_game_igdb_term",
                columnNames = {"steam_app_id", "taxonomy_term_id"}),
        indexes = {
                @Index(name = "idx_steam_game_igdb_app", columnList = "steam_app_id,taxonomy_term_id"),
                @Index(name = "idx_steam_game_igdb_term", columnList = "taxonomy_term_id,steam_app_id")
        })
public class SteamGameIgdbTerm {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "steam_app_id", referencedColumnName = "steam_app_id", nullable = false)
    private SteamGame game;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "taxonomy_term_id", nullable = false)
    private IgdbTaxonomyTerm taxonomyTerm;

    protected SteamGameIgdbTerm() {}

    public SteamGameIgdbTerm(SteamGame game, IgdbTaxonomyTerm taxonomyTerm) {
        this.game = game;
        this.taxonomyTerm = taxonomyTerm;
    }

    public Long getId() { return id; }
    public SteamGame getGame() { return game; }
    public IgdbTaxonomyTerm getTaxonomyTerm() { return taxonomyTerm; }
}
