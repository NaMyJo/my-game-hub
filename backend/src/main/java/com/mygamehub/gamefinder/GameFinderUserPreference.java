package com.mygamehub.gamefinder;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "game_finder_user_preferences")
public class GameFinderUserPreference {
    @Id @Column(name = "firebase_uid", length = 128) private String firebaseUid;
    @Column(name = "selected_app_ids", columnDefinition = "TEXT") private String selectedAppIds;
    @Column(name = "preferred_tags", columnDefinition = "TEXT") private String preferredTags;
    private int priceMin;
    private int priceMax = 100000;
    private boolean includeAdult;
    private int playerMin = 1;
    private int playerMax = 15;
    @Column(nullable = false) private Instant updatedAt;
    protected GameFinderUserPreference() {}
    public GameFinderUserPreference(String uid) { firebaseUid=uid; updatedAt=Instant.now(); }
    public void update(Collection<Long> ids, Collection<String> tags, int priceMin, int priceMax,
            boolean includeAdult, int playerMin, int playerMax) {
        selectedAppIds=join(ids); preferredTags=String.join("|", tags); this.priceMin=priceMin;
        this.priceMax=priceMax; this.includeAdult=includeAdult; this.playerMin=playerMin;
        this.playerMax=playerMax; updatedAt=Instant.now();
    }
    private static String join(Collection<Long> ids){return ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining("|"));}
    public List<Long> selectedIds(){if(selectedAppIds==null||selectedAppIds.isBlank())return List.of();return Arrays.stream(selectedAppIds.split("\\|")).map(Long::valueOf).toList();}
    public List<String> tags(){if(preferredTags==null||preferredTags.isBlank())return List.of();return Arrays.asList(preferredTags.split("\\|"));}
    public int getPriceMin(){return priceMin;} public int getPriceMax(){return priceMax;}
    public boolean isIncludeAdult(){return includeAdult;} public int getPlayerMin(){return playerMin;}
    public int getPlayerMax(){return playerMax;}
}
