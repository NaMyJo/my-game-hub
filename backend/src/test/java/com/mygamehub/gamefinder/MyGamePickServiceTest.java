package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(MyGamePickService.class)
class MyGamePickServiceTest {
    @Autowired MyGamePickService service;
    @Autowired MyGamePickRepository picks;
    @Autowired SteamGameRepository games;

    @Test
    void storesOnceSeparatesUsersAndRemovesOnlyRelation() {
        games.save(new SteamGame(570, "Dota 2", 1, 1));

        service.add("uid-a", 570);
        service.add("uid-a", 570);
        service.add("uid-b", 570);

        assertThat(service.list("uid-a")).extracting(value -> value.steamAppId())
                .containsExactly(570L);
        assertThat(service.list("uid-b")).hasSize(1);
        assertThat(picks.count()).isEqualTo(2);

        service.remove("uid-a", 570);
        assertThat(service.list("uid-a")).isEmpty();
        assertThat(service.list("uid-b")).hasSize(1);
        assertThat(games.findBySteamAppId(570L)).isPresent();
    }

    @Test
    void listsNewestPickFirst() {
        games.save(new SteamGame(570, "Dota 2", 1, 1));
        games.save(new SteamGame(730, "Counter-Strike 2", 1, 1));
        picks.save(new MyGamePick("uid", 570, Instant.parse("2026-01-01T00:00:00Z")));
        picks.save(new MyGamePick("uid", 730, Instant.parse("2026-01-02T00:00:00Z")));

        assertThat(service.list("uid")).extracting(value -> value.steamAppId())
                .containsExactly(730L, 570L);
    }

    @Test
    void rejectsUnknownSteamAppId() {
        assertThatThrownBy(() -> service.add("uid", 999999))
                .isInstanceOf(ResponseStatusException.class);
    }
}
