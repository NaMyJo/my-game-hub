package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderPreferenceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({GameFinderPreferenceService.class, GameTagTaxonomy.class})
class GameFinderPreferenceServiceTest {
    @Autowired GameFinderPreferenceService service;
    @Autowired SteamGameRepository games;

    @Test
    void newUserReceivesDefaultsInsteadOfNotFound() {
        var result = service.get("new-user");

        assertThat(result.selectedGames()).isEmpty();
        assertThat(result.recentGames()).isEmpty();
        assertThat(result.preferredTags()).isEmpty();
        assertThat(result.priceMin()).isZero();
        assertThat(result.priceMax()).isEqualTo(100000);
        assertThat(result.includeAdult()).isFalse();
        assertThat(result.playerMin()).isEqualTo(1);
        assertThat(result.playerMax()).isEqualTo(15);
    }

    @Test
    void restoresLastPreferenceAndRecentSeedsWithoutMixingFirebaseUsers() {
        games.save(new SteamGame(570, "Dota 2", 1, 1));
        games.save(new SteamGame(730, "Counter-Strike 2", 1, 1));

        service.save("uid-a", request(List.of(570L), List.of("AOS")));
        service.save("uid-b", request(List.of(730L), List.of("협동")));

        var first = service.get("uid-a");
        var second = service.get("uid-b");
        assertThat(first.selectedGames()).extracting(v -> v.steamAppId()).containsExactly(570L);
        assertThat(first.recentGames()).extracting(v -> v.steamAppId()).containsExactly(570L);
        assertThat(first.preferredTags()).containsExactly("moba");
        assertThat(second.selectedGames()).extracting(v -> v.steamAppId()).containsExactly(730L);
        assertThat(second.recentGames()).extracting(v -> v.steamAppId()).containsExactly(730L);
        assertThat(second.preferredTags()).containsExactly("coop");
    }

    private GameFinderPreferenceRequest request(List<Long> ids, List<String> tags) {
        return new GameFinderPreferenceRequest(ids, tags, 1000, 50000, false, 1, 4);
    }
}
