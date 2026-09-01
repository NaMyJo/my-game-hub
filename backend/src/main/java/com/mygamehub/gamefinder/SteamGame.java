package com.mygamehub.gamefinder;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "steam_games", indexes = {
        @Index(name = "idx_steam_games_name", columnList = "name"),
        @Index(name = "idx_steam_games_candidate", columnList = "store_type, metadata_updated_at")
})
public class SteamGame {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "steam_app_id", nullable = false, unique = true)
    private Long steamAppId;
    @Column(name = "igdb_game_id") private Long igdbGameId;
    @Column(nullable = false, length = 500) private String name;
    @Column(name = "store_type", length = 30) private String storeType;
    @Column(name = "header_image_url", length = 1000) private String headerImageUrl;
    @Column(name = "short_description", columnDefinition = "TEXT") private String shortDescription;
    @Column(name = "is_free") private Boolean isFree;
    @Column(name = "price_currency", length = 10) private String priceCurrency;
    @Column(name = "price_original") private Integer priceOriginal;
    @Column(name = "price_current") private Integer priceCurrent;
    @Column(name = "discount_percent") private Integer discountPercent;
    @Column(name = "adult_status", length = 20) private String adultStatus = "UNKNOWN";
    @Column(name = "required_age") private Integer requiredAge;
    @Column(name = "release_date") private LocalDate releaseDate;
    @Column(name = "release_date_text", length = 100) private String releaseDateText;
    @Column(name = "coming_soon") private boolean comingSoon;
    @Column(name = "early_access") private Boolean earlyAccess;
    @Column(name = "steam_last_modified") private Long steamLastModified;
    @Column(name = "steam_price_change_number") private Long steamPriceChangeNumber;
    @Column(name = "metadata_updated_at") private Instant metadataUpdatedAt;
    @Column(name = "price_updated_at") private Instant priceUpdatedAt;
    @Column(name = "igdb_updated_at") private Instant igdbUpdatedAt;
    @Enumerated(EnumType.STRING) @Column(name = "metadata_status", length = 30)
    private EnrichmentStatus metadataStatus;
    @Column(name = "metadata_last_attempt_at") private Instant metadataLastAttemptAt;
    @Enumerated(EnumType.STRING) @Column(name = "igdb_status", length = 30)
    private EnrichmentStatus igdbStatus;
    @Column(name = "igdb_last_attempt_at") private Instant igdbLastAttemptAt;
    @Enumerated(EnumType.STRING) @Column(name="lifecycle_status",length=20)
    private CatalogLifecycleStatus lifecycleStatus;
    @Column(name="last_seen_at") private Instant lastSeenAt;
    @Column(name="reconciliation_generation",length=60) private String reconciliationGeneration;
    @Column(name = "single_player") private Boolean singlePlayer;
    @Column(name = "multiplayer") private Boolean multiplayer;
    @Column(name = "online_coop") private Boolean onlineCoop;
    @Column(name = "offline_coop") private Boolean offlineCoop;
    @Column(name = "min_players") private Integer minPlayers;
    @Column(name = "max_players") private Integer maxPlayers;
    @Column(name = "online_max_players") private Integer onlineMaxPlayers;
    @Column(name = "online_coop_max_players") private Integer onlineCoopMaxPlayers;
    @Column(columnDefinition = "TEXT") private String genres;
    @Column(columnDefinition = "TEXT") private String categories;

    protected SteamGame() {}
    public SteamGame(long steamAppId, String name, long lastModified, long priceChangeNumber) {
        this.steamAppId = steamAppId; this.name = name;
        this.steamLastModified = lastModified;
        this.steamPriceChangeNumber = priceChangeNumber;
        this.storeType = "game";
    }

    public void updateCatalog(String name, long lastModified, long priceChangeNumber) {
        if (this.steamPriceChangeNumber != null
                && this.steamPriceChangeNumber != priceChangeNumber) {
            this.priceUpdatedAt = null;
        }
        this.name = name; this.steamLastModified = lastModified;
        this.steamPriceChangeNumber = priceChangeNumber;
    }
    public void updateStoreDetail(String type, String image, String description,
            Boolean free, String currency, Integer original, Integer current,
            Integer discount, Integer requiredAge, String adult, LocalDate releaseDate,
            String releaseText, boolean comingSoon, Boolean earlyAccess,
            Set<String> genres, Set<String> categories, Boolean single,
            Boolean multiplayer, Boolean onlineCoop, Boolean offlineCoop) {
        this.storeType = type; this.headerImageUrl = image;
        this.shortDescription = description; this.isFree = free;
        this.priceCurrency = currency; this.priceOriginal = original;
        this.priceCurrent = current; this.discountPercent = discount;
        this.requiredAge = requiredAge; this.adultStatus = adult; this.releaseDate = releaseDate;
        this.releaseDateText = releaseText; this.comingSoon = comingSoon;
        this.earlyAccess = earlyAccess; this.genres = join(genres);
        this.categories = join(categories); this.singlePlayer = single;
        this.multiplayer = multiplayer; this.onlineCoop = onlineCoop;
        this.offlineCoop = offlineCoop; this.metadataUpdatedAt = Instant.now();
        this.priceUpdatedAt = Instant.now();
        this.metadataLastAttemptAt = Instant.now(); this.metadataStatus = EnrichmentStatus.SUCCESS;
    }
    public void updateIgdb(Long id, Integer min, Integer max, Integer onlineMax,
            Integer coopMax, Boolean multiplayer, Boolean onlineCoop,
            Boolean offlineCoop) {
        this.igdbGameId=id; this.minPlayers=min; this.maxPlayers=max;
        this.onlineMaxPlayers=onlineMax; this.onlineCoopMaxPlayers=coopMax;
        this.multiplayer=multiplayer; this.onlineCoop=onlineCoop;
        this.offlineCoop=offlineCoop; this.igdbUpdatedAt=Instant.now();
        this.igdbLastAttemptAt=Instant.now(); this.igdbStatus=EnrichmentStatus.SUCCESS;
    }
    public void markMetadataNotFound(){metadataLastAttemptAt=Instant.now();metadataStatus=EnrichmentStatus.NOT_FOUND;lifecycleStatus=CatalogLifecycleStatus.UNAVAILABLE;}
    public void markMetadataFailure(boolean retryable){metadataLastAttemptAt=Instant.now();metadataStatus=retryable?EnrichmentStatus.RETRYABLE_FAILURE:EnrichmentStatus.PERMANENT_FAILURE;}
    public void markIgdbNotFound(){igdbLastAttemptAt=Instant.now();igdbUpdatedAt=Instant.now();igdbStatus=EnrichmentStatus.NOT_FOUND;}
    public void markIgdbFailure(boolean retryable){igdbLastAttemptAt=Instant.now();igdbStatus=retryable?EnrichmentStatus.RETRYABLE_FAILURE:EnrichmentStatus.PERMANENT_FAILURE;}
    public void markRemoved(){lifecycleStatus=CatalogLifecycleStatus.REMOVED;}
    public boolean isDiscoverable(){return lifecycleStatus==null||lifecycleStatus==CatalogLifecycleStatus.ACTIVE;}
    private String join(Set<String> values) { return values == null ? "" : String.join("|", values); }
    private Set<String> split(String value) {
        if (value == null || value.isBlank()) return Set.of();
        return Arrays.stream(value.split("\\|")).filter(v -> !v.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
    public Set<String> genreSet() { return split(genres); }
    public Set<String> categorySet() { return split(categories); }
    public Set<String> features() { var out=new LinkedHashSet<>(genreSet()); out.addAll(categorySet()); return out; }
    public Long getId(){return id;} public Long getSteamAppId(){return steamAppId;}
    public Long getIgdbGameId(){return igdbGameId;} public String getName(){return name;}
    public String getStoreType(){return storeType;} public String getHeaderImageUrl(){return headerImageUrl;}
    public String getShortDescription(){return shortDescription;} public Boolean getIsFree(){return isFree;}
    public String getPriceCurrency(){return priceCurrency;} public Integer getPriceOriginal(){return priceOriginal;}
    public Integer getPriceCurrent(){return priceCurrent;} public Integer getDiscountPercent(){return discountPercent;}
    public Integer getRequiredAge(){return requiredAge;} public String getAdultStatus(){return adultStatus;} public LocalDate getReleaseDate(){return releaseDate;}
    public String getReleaseDateText(){return releaseDateText;} public boolean isComingSoon(){return comingSoon;}
    public Boolean getEarlyAccess(){return earlyAccess;} public Long getSteamLastModified(){return steamLastModified;}
    public Long getSteamPriceChangeNumber(){return steamPriceChangeNumber;} public Instant getMetadataUpdatedAt(){return metadataUpdatedAt;}
    public Instant getPriceUpdatedAt(){return priceUpdatedAt;} public Instant getIgdbUpdatedAt(){return igdbUpdatedAt;}
    public Boolean getSinglePlayer(){return singlePlayer;} public Boolean getMultiplayer(){return multiplayer;}
    public Boolean getOnlineCoop(){return onlineCoop;} public Boolean getOfflineCoop(){return offlineCoop;}
    public Integer getMinPlayers(){return minPlayers;} public Integer getMaxPlayers(){return maxPlayers;}
    public Integer getOnlineMaxPlayers(){return onlineMaxPlayers;} public Integer getOnlineCoopMaxPlayers(){return onlineCoopMaxPlayers;}
    public EnrichmentStatus getMetadataStatus(){return metadataStatus;} public Instant getMetadataLastAttemptAt(){return metadataLastAttemptAt;}
    public EnrichmentStatus getIgdbStatus(){return igdbStatus;} public Instant getIgdbLastAttemptAt(){return igdbLastAttemptAt;}
    public CatalogLifecycleStatus getLifecycleStatus(){return lifecycleStatus;} public Instant getLastSeenAt(){return lastSeenAt;}
    public String getReconciliationGeneration(){return reconciliationGeneration;}
}
