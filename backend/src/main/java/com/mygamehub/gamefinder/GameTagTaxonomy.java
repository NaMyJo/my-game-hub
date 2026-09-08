package com.mygamehub.gamefinder;

import org.springframework.stereotype.Component;
import java.util.*;

@Component
public class GameTagTaxonomy {
    public static final String STEAM_VERSION = "steam-v1-19";
    public static final String IGDB_VERSION = "igdb-v2-28";
    public static final String CURRENT_VERSION = "steam-igdb-v2-47";

    private static final Set<String> CURRENT_TAGS = Set.of(
            "action", "adventure", "rpg", "strategy", "simulation", "sports", "racing",
            "casual", "indie", "massively-multiplayer", "free-to-play", "early-access",
            "singleplayer", "multiplayer", "coop", "online-coop", "local-coop", "pvp", "online-pvp",
            "horror", "moba", "open-world", "survival", "stealth", "turn-based-strategy",
            "soulslike", "jrpg", "turn-based", "roguelike", "roguelite", "metroidvania",
            "tower-defense", "bullet-hell", "fps", "third-person-shooter", "fantasy", "sci-fi",
            "mystery", "warfare", "medieval", "space", "military", "post-apocalyptic",
            "crafting", "story-rich", "relaxing", "choices-matter");
    private static final Set<String> FEATURE_TAGS = Set.of(
            "singleplayer", "multiplayer", "coop", "online-coop", "local-coop", "pvp", "online-pvp");
    private static final Set<String> GENRE_TAGS = Set.of(
            "action", "adventure", "rpg", "strategy", "simulation", "sports", "racing",
            "casual", "indie", "massively-multiplayer", "horror", "moba", "survival",
            "turn-based-strategy", "jrpg", "roguelike", "roguelite", "metroidvania",
            "tower-defense", "bullet-hell");
    private static final Set<String> THEME_TAGS = Set.of(
            "fantasy", "sci-fi", "mystery", "warfare", "medieval", "space", "military",
            "post-apocalyptic");
    private static final Set<String> STYLE_TAGS = Set.of(
            "open-world", "stealth", "soulslike", "turn-based", "fps", "third-person-shooter",
            "crafting", "story-rich", "relaxing", "choices-matter");
    private static final Map<String, String> DISPLAY = new LinkedHashMap<>();
    private static final Map<String, String> ALIASES = new LinkedHashMap<>();
    private static final Map<TermKey, String> IGDB_MAPPING = Map.ofEntries(
            mapping(IgdbTaxonomySourceType.THEME, 19, "horror"),
            mapping(IgdbTaxonomySourceType.GENRE, 36, "moba"),
            mapping(IgdbTaxonomySourceType.THEME, 38, "open-world"),
            mapping(IgdbTaxonomySourceType.THEME, 21, "survival"),
            mapping(IgdbTaxonomySourceType.THEME, 23, "stealth"),
            mapping(IgdbTaxonomySourceType.GENRE, 16, "turn-based-strategy"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 17326, "soulslike"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 521, "jrpg"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 415, "turn-based"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 416, "roguelike"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 17292, "roguelite"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 477, "metroidvania"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 77, "tower-defense"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 911, "bullet-hell"),
            mapping(IgdbTaxonomySourceType.THEME, 17, "fantasy"),
            mapping(IgdbTaxonomySourceType.THEME, 18, "sci-fi"),
            mapping(IgdbTaxonomySourceType.THEME, 43, "mystery"),
            mapping(IgdbTaxonomySourceType.THEME, 39, "warfare"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 151, "medieval"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 974, "space"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 563, "military"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 69, "post-apocalyptic"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 510, "crafting"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 2426, "story-rich"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 2084, "relaxing"),
            mapping(IgdbTaxonomySourceType.KEYWORD, 3534, "choices-matter"));

    static {
        current("action", "액션"); current("adventure", "어드벤처"); current("rpg", "RPG");
        current("strategy", "전략"); current("simulation", "시뮬레이션"); current("sports", "스포츠");
        current("racing", "레이싱"); current("casual", "캐주얼"); current("indie", "인디");
        current("massively-multiplayer", "대규모 멀티플레이", "massively multiplayer", "massively multiplayer online");
        current("free-to-play", "무료 플레이", "free to play", "f2p"); current("early-access", "앞서 해보기", "early access");
        current("singleplayer", "싱글플레이", "single-player", "single player", "싱글 플레이", "싱글 플레이어");
        current("multiplayer", "멀티플레이", "multi-player", "multi player", "멀티", "멀티플레이어");
        current("coop", "협동", "co-op", "코옵", "cooperative"); current("online-coop", "온라인 협동", "online co-op", "온라인 코옵");
        current("local-coop", "로컬 협동", "local co-op", "shared/split screen co-op", "shared/split screen");
        current("pvp", "PvP", "player vs. player", "플레이어 대전"); current("online-pvp", "온라인 PvP", "online pvp");
        current("horror", "공포"); current("moba", "AOS/MOBA", "aos"); current("open-world", "오픈 월드", "open world");
        current("survival", "생존"); current("stealth", "잠입"); current("turn-based-strategy", "턴제 전략", "turn based strategy", "tbs");
        current("soulslike", "소울라이크", "souls-like"); current("jrpg", "JRPG"); current("turn-based", "턴제", "turn based");
        current("roguelike", "로그라이크", "rogue-like"); current("roguelite", "로그라이트", "rogue-lite");
        current("metroidvania", "메트로배니아"); current("tower-defense", "타워 디펜스", "tower defense");
        current("bullet-hell", "탄막", "bullet hell"); current("fps", "1인칭 슈팅", "first-person shooter", "first person shooter");
        current("third-person-shooter", "3인칭 슈팅", "third person shooter", "tps"); current("fantasy", "판타지");
        current("sci-fi", "SF", "science fiction", "science-fiction"); current("mystery", "미스터리");
        current("warfare", "전쟁"); current("medieval", "중세"); current("space", "우주"); current("military", "밀리터리");
        current("post-apocalyptic", "포스트 아포칼립스", "post apocalyptic"); current("crafting", "제작");
        current("story-rich", "풍부한 스토리", "story rich"); current("relaxing", "힐링", "편안한");
        current("choices-matter", "선택의 중요성", "choices matter");
        legacy("deckbuilder", "덱빌딩", "deck-building"); legacy("card-game", "카드 게임", "card game");
        legacy("zombies", "좀비", "zombie"); legacy("difficult", "어려움", "hard");
    }

    public Optional<String> normalize(String value) {
        if (value == null) return Optional.empty();
        return Optional.ofNullable(ALIASES.get(value.trim().toLowerCase(Locale.ROOT)));
    }
    public Set<String> parse(String query, Collection<String> tags) {
        Set<String> result = new LinkedHashSet<>();
        if (tags != null) tags.forEach(value -> normalize(value).ifPresent(result::add));
        if (query != null) {
            normalize(query).ifPresent(result::add);
            for (String value : query.split("[,+\\s]+")) normalize(value).ifPresent(result::add);
        }
        return result;
    }
    public Set<String> fromSteam(SteamGame game) {
        Set<String> result = new LinkedHashSet<>();
        game.genreSet().forEach(value -> addCurrent(result, value));
        game.categorySet().forEach(value -> addCurrent(result, value));
        if (Boolean.TRUE.equals(game.getSinglePlayer())) result.add("singleplayer");
        if (Boolean.TRUE.equals(game.getMultiplayer())) result.add("multiplayer");
        if (Boolean.TRUE.equals(game.getOnlineCoop())) { result.add("coop"); result.add("online-coop"); }
        if (Boolean.TRUE.equals(game.getOfflineCoop())) { result.add("coop"); result.add("local-coop"); }
        if (Boolean.TRUE.equals(game.getIsFree())) result.add("free-to-play");
        if (Boolean.TRUE.equals(game.getEarlyAccess())) result.add("early-access");
        return result;
    }
    public Set<String> fromIgdb(Collection<IgdbTaxonomyValue> values) {
        Set<String> result = new LinkedHashSet<>();
        Set<TermKey> keys = new LinkedHashSet<>();
        if (values != null) values.forEach(value -> keys.add(new TermKey(value.sourceType(), value.igdbTermId())));
        keys.forEach(key -> Optional.ofNullable(IGDB_MAPPING.get(key)).ifPresent(result::add));
        if (keys.contains(new TermKey(IgdbTaxonomySourceType.GENRE, 5))) {
            if (keys.contains(new TermKey(IgdbTaxonomySourceType.PERSPECTIVE, 1))) result.add("fps");
            if (keys.contains(new TermKey(IgdbTaxonomySourceType.PERSPECTIVE, 2))) result.add("third-person-shooter");
        }
        return result;
    }
    private void addCurrent(Set<String> result, String value) {
        normalize(value).filter(CURRENT_TAGS::contains).ifPresent(result::add);
    }
    public Set<String> currentCanonicalNames() { return CURRENT_TAGS; }
    public String display(String canonical) { return DISPLAY.getOrDefault(canonical, canonical); }
    public String type(String canonical) {
        if (FEATURE_TAGS.contains(canonical)) return "FEATURE";
        if (GENRE_TAGS.contains(canonical)) return "GENRE";
        if (THEME_TAGS.contains(canonical)) return "THEME";
        if (STYLE_TAGS.contains(canonical)) return "STYLE";
        return "TAG";
    }
    private static void current(String canonical, String display, String... values) {
        DISPLAY.put(canonical, display); alias(canonical, canonical, display); alias(canonical, values);
    }
    private static void legacy(String canonical, String display, String... values) {
        DISPLAY.put(canonical, display); alias(canonical, canonical, display); alias(canonical, values);
    }
    private static void alias(String canonical, String... values) {
        for (String value : values) ALIASES.put(value.trim().toLowerCase(Locale.ROOT), canonical);
    }
    private static Map.Entry<TermKey, String> mapping(IgdbTaxonomySourceType sourceType, long id, String canonical) {
        return Map.entry(new TermKey(sourceType, id), canonical);
    }
    private record TermKey(IgdbTaxonomySourceType sourceType, long id) {}
}
