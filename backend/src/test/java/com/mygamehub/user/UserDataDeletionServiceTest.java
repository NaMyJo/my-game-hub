package com.mygamehub.user;

import com.mygamehub.game.GameAccountRepository;
import com.mygamehub.gamefinder.GameFinderRecentSeedRepository;
import com.mygamehub.gamefinder.GameFinderUserPreferenceRepository;
import com.mygamehub.gamefinder.MyGamePickRepository;
import com.mygamehub.gameidentity.GameIdentityCard;
import com.mygamehub.gameidentity.GameIdentityCardRepository;
import com.mygamehub.gameidentity.GameIdentityHistoryRepository;
import com.mygamehub.gameprofile.GameProfileSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserDataDeletionServiceTest {

    @Test
    void deletesEveryUidScopedRepositoryWithoutTouchingCatalogRepositories()
            throws Exception {
        GameIdentityCardRepository cards =
                mock(GameIdentityCardRepository.class);
        GameIdentityHistoryRepository history =
                mock(GameIdentityHistoryRepository.class);
        GameProfileSummaryRepository summaries =
                mock(GameProfileSummaryRepository.class);
        GameFinderUserPreferenceRepository preferences =
                mock(GameFinderUserPreferenceRepository.class);
        GameFinderRecentSeedRepository recents =
                mock(GameFinderRecentSeedRepository.class);
        MyGamePickRepository picks = mock(MyGamePickRepository.class);
        GameAccountRepository accounts = mock(GameAccountRepository.class);
        AppUserRepository users = mock(AppUserRepository.class);
        GameIdentityCard card = mock(GameIdentityCard.class);
        when(cards.findAllByFirebaseUidOrderByCreatedAtDesc("uid"))
                .thenReturn(List.of(card));

        UserDataDeletionService service = new UserDataDeletionService(
                cards,
                history,
                summaries,
                preferences,
                recents,
                picks,
                accounts,
                users
        );

        service.deleteByFirebaseUid("uid");

        verify(cards).deleteAll(List.of(card));
        verify(history).deleteByUserUid("uid");
        verify(summaries).deleteByUserUid("uid");
        verify(preferences).deleteById("uid");
        verify(recents).deleteAllByFirebaseUid("uid");
        verify(picks).deleteAllByFirebaseUid("uid");
        verify(accounts).deleteAllByFirebaseUid("uid");
        verify(users).deleteById("uid");

        var method = UserDataDeletionService.class.getMethod(
                "deleteByFirebaseUid",
                String.class
        );
        assertThat(method.isAnnotationPresent(Transactional.class)).isTrue();
    }
}
