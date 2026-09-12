package com.mygamehub.user;

import com.mygamehub.game.GameAccount;
import com.mygamehub.game.GameAccountRepository;
import com.mygamehub.game.GameType;
import com.mygamehub.gamefinder.GameFinderRecentSeed;
import com.mygamehub.gamefinder.GameFinderRecentSeedRepository;
import com.mygamehub.gamefinder.GameFinderUserPreference;
import com.mygamehub.gamefinder.GameFinderUserPreferenceRepository;
import com.mygamehub.gamefinder.MyGamePick;
import com.mygamehub.gamefinder.MyGamePickRepository;
import com.mygamehub.gamefinder.SteamGame;
import com.mygamehub.gamefinder.SteamGameRepository;
import com.mygamehub.gameidentity.GameIdentityCard;
import com.mygamehub.gameidentity.GameIdentityCardRepository;
import com.mygamehub.gameidentity.GameIdentityEntry;
import com.mygamehub.gameidentity.GameIdentityEntryRepository;
import com.mygamehub.gameidentity.GameIdentityEvaluationType;
import com.mygamehub.gameidentity.GameIdentityHistory;
import com.mygamehub.gameidentity.GameIdentityHistoryRepository;
import com.mygamehub.gameprofile.GameProfileSummary;
import com.mygamehub.gameprofile.GameProfileSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(UserDataDeletionService.class)
class UserDataDeletionIntegrationTest {

    private static final String UID = "delete-target";

    @Autowired UserDataDeletionService service;
    @Autowired AppUserRepository users;
    @Autowired GameAccountRepository accounts;
    @Autowired GameProfileSummaryRepository summaries;
    @Autowired GameIdentityCardRepository identityCards;
    @Autowired GameIdentityEntryRepository identityEntries;
    @Autowired GameIdentityHistoryRepository identityHistory;
    @Autowired GameFinderUserPreferenceRepository preferences;
    @Autowired GameFinderRecentSeedRepository recentSeeds;
    @Autowired MyGamePickRepository picks;
    @Autowired SteamGameRepository steamGames;

    @Test
    void removesAllUserDataAndKeepsSharedCatalogData() {
        seedUserData();
        SteamGame catalogGame = steamGames.save(
                new SteamGame(570, "Dota 2", 1, 1)
        );

        service.deleteByFirebaseUid(UID);
        service.deleteByFirebaseUid(UID);

        assertThat(users.findById(UID)).isEmpty();
        assertThat(accounts.countByFirebaseUid(UID)).isZero();
        assertThat(summaries.findByUserUid(UID)).isEmpty();
        assertThat(identityCards
                .findAllByFirebaseUidOrderByCreatedAtDesc(UID)).isEmpty();
        assertThat(identityEntries.count()).isZero();
        assertThat(identityHistory.findByUserUid(UID)).isEmpty();
        assertThat(preferences.findById(UID)).isEmpty();
        assertThat(recentSeeds
                .findByFirebaseUidOrderBySelectedAtDesc(
                        UID,
                        org.springframework.data.domain.Pageable.unpaged()
                )).isEmpty();
        assertThat(picks.findAllByFirebaseUidOrderByCreatedAtDesc(UID))
                .isEmpty();

        assertThat(steamGames.findById(catalogGame.getId())).isPresent();
    }

    private void seedUserData() {
        users.save(new AppUser(UID, "user@example.com", "User", null));

        GameAccount account = accounts.save(
                new GameAccount(UID, GameType.LOST_ARK, "character")
        );

        summaries.save(new GameProfileSummary(
                UID,
                "The Gamer",
                null,
                1,
                "evaluation",
                "data:image/png;base64,profile"
        ));

        GameIdentityCard card = new GameIdentityCard(
                UID,
                "The Gamer",
                null,
                GameIdentityEvaluationType.RPG_ONLY,
                "evaluation"
        );
        card.addEntry(new GameIdentityEntry(
                account.getId(),
                GameType.LOST_ARK,
                "character",
                "전투력",
                "1000",
                null,
                false,
                0
        ));
        identityCards.save(card);

        identityHistory.save(new GameIdentityHistory(
                UID,
                "identity-number",
                "The Gamer",
                "2026.09.12",
                null,
                "evaluation",
                "{}"
        ));
        preferences.save(new GameFinderUserPreference(UID));
        recentSeeds.save(new GameFinderRecentSeed(UID, 570, Instant.now()));
        picks.save(new MyGamePick(UID, 570, Instant.now()));
    }
}
