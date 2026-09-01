package com.mygamehub.gamefinder;
import org.springframework.stereotype.Component;
import java.util.*;
@Component
public class GameTagTaxonomy {
    private static final Map<String,String> ALIASES=new HashMap<>();
    private static final Map<String,String> DISPLAY=Map.ofEntries(
      Map.entry("coop","협동"),Map.entry("online-coop","온라인 협동"),Map.entry("multiplayer","멀티플레이"),Map.entry("singleplayer","싱글플레이"),Map.entry("pvp","PvP"),Map.entry("online-pvp","온라인 PvP"),Map.entry("action","액션"),Map.entry("strategy","전략"),Map.entry("rpg","RPG"),Map.entry("indie","인디"),Map.entry("free-to-play","무료 플레이"),Map.entry("moba","AOS/MOBA"),Map.entry("roguelike","로그라이크"),Map.entry("roguelite","로그라이트"),Map.entry("open-world","오픈월드"),Map.entry("soulslike","소울라이크"),Map.entry("survival","생존"));
    static { alias("coop","협동","코옵","co-op","coop");alias("multiplayer","멀티","멀티플레이","멀티플레이어","multiplayer");alias("moba","aos","moba");alias("roguelike","로그라이크","roguelike");alias("roguelite","로그라이트","roguelite");alias("open-world","오픈월드","open world","open-world");alias("soulslike","소울라이크","soulslike");alias("survival","생존","survival");DISPLAY.forEach((k,v)->{ALIASES.putIfAbsent(k,k);ALIASES.putIfAbsent(v.toLowerCase(Locale.ROOT),k);});ALIASES.put("온라인 협동","online-coop");ALIASES.put("온라인 pvp","online-pvp");ALIASES.put("싱글 플레이어","singleplayer");ALIASES.put("무료 플레이","free-to-play");}
    private static void alias(String c,String... values){for(String v:values)ALIASES.put(v.toLowerCase(Locale.ROOT),c);}
    public Optional<String> normalize(String value){if(value==null)return Optional.empty();return Optional.ofNullable(ALIASES.get(value.trim().toLowerCase(Locale.ROOT)));}
    public Set<String> parse(String query,Collection<String> tags){Set<String> out=new LinkedHashSet<>();if(tags!=null)tags.forEach(v->normalize(v).ifPresent(out::add));if(query!=null){normalize(query).ifPresent(out::add);for(String v:query.split("[,+\\s]+"))normalize(v).ifPresent(out::add);}return out;}
    public Set<String> fromSteam(SteamGame game){Set<String> out=new LinkedHashSet<>();game.genreSet().forEach(v->normalize(v).ifPresent(out::add));game.categorySet().forEach(v->normalize(v).ifPresent(out::add));return out;}
    public String display(String canonical){return DISPLAY.getOrDefault(canonical,canonical);}
    public String type(String c){return Set.of("coop","online-coop","multiplayer","singleplayer","pvp","online-pvp").contains(c)?"FEATURE":Set.of("action","strategy","rpg","indie","moba").contains(c)?"GENRE":"TAG";}
}
