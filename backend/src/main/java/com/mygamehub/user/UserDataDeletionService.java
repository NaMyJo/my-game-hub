package com.mygamehub.user;

import com.mygamehub.game.GameAccountRepository;
import com.mygamehub.gamefinder.GameFinderRecentSeedRepository;
import com.mygamehub.gamefinder.GameFinderUserPreferenceRepository;
import com.mygamehub.gamefinder.MyGamePickRepository;
import com.mygamehub.gameidentity.GameIdentityCardRepository;
import com.mygamehub.gameidentity.GameIdentityHistoryRepository;
import com.mygamehub.gameprofile.GameProfileSummaryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserDataDeletionService {

    private final GameIdentityCardRepository identityCards;
    private final GameIdentityHistoryRepository identityHistory;
    private final GameProfileSummaryRepository profileSummaries;
    private final GameFinderUserPreferenceRepository finderPreferences;
    private final GameFinderRecentSeedRepository recentSeeds;
    private final MyGamePickRepository gamePicks;
    private final GameAccountRepository gameAccounts;
    private final AppUserRepository appUsers;

    public UserDataDeletionService(
            GameIdentityCardRepository identityCards,
            GameIdentityHistoryRepository identityHistory,
            GameProfileSummaryRepository profileSummaries,
            GameFinderUserPreferenceRepository finderPreferences,
            GameFinderRecentSeedRepository recentSeeds,
            MyGamePickRepository gamePicks,
            GameAccountRepository gameAccounts,
            AppUserRepository appUsers
    ) {
        this.identityCards = identityCards;
        this.identityHistory = identityHistory;
        this.profileSummaries = profileSummaries;
        this.finderPreferences = finderPreferences;
        this.recentSeeds = recentSeeds;
        this.gamePicks = gamePicks;
        this.gameAccounts = gameAccounts;
        this.appUsers = appUsers;
    }

    @Transactional
    public void deleteByFirebaseUid(String firebaseUid) {
        identityCards.deleteAll(
                identityCards.findAllByFirebaseUidOrderByCreatedAtDesc(firebaseUid)
        );
        identityHistory.deleteByUserUid(firebaseUid);
        profileSummaries.deleteByUserUid(firebaseUid);
        finderPreferences.deleteById(firebaseUid);
        recentSeeds.deleteAllByFirebaseUid(firebaseUid);
        gamePicks.deleteAllByFirebaseUid(firebaseUid);
        gameAccounts.deleteAllByFirebaseUid(firebaseUid);
        appUsers.deleteById(firebaseUid);
    }
}
