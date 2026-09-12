package com.mygamehub.user;

import com.google.firebase.auth.FirebaseAuthException;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserAccountDeletionServiceTest {

    private final UserDataDeletionService dataDeletionService =
            mock(UserDataDeletionService.class);
    private final FirebaseUserDeletionService firebaseDeletionService =
            mock(FirebaseUserDeletionService.class);
    private final UserAccountDeletionService service =
            new UserAccountDeletionService(
                    dataDeletionService,
                    firebaseDeletionService
            );

    @Test
    void commitsUserDataDeletionBeforeDeletingFirebaseAccount() throws Exception {
        service.deleteAccount("firebase-uid");

        InOrder order = inOrder(dataDeletionService, firebaseDeletionService);
        order.verify(dataDeletionService).deleteByFirebaseUid("firebase-uid");
        order.verify(firebaseDeletionService).deleteUser("firebase-uid");
    }

    @Test
    void leavesFirebaseAccountAvailableWhenDatabaseDeletionFails() throws Exception {
        doThrow(new IllegalStateException("database unavailable"))
                .when(dataDeletionService)
                .deleteByFirebaseUid("firebase-uid");

        assertThatThrownBy(() -> service.deleteAccount("firebase-uid"))
                .isInstanceOf(AccountDeletionException.class)
                .hasMessageContaining("다시 시도");

        verify(firebaseDeletionService, org.mockito.Mockito.never())
                .deleteUser("firebase-uid");
    }

    @Test
    void reportsRecoverablePartialFailureWhenFirebaseDeletionFails()
            throws Exception {
        FirebaseAuthException firebaseError = mock(FirebaseAuthException.class);
        doThrow(firebaseError)
                .when(firebaseDeletionService)
                .deleteUser("firebase-uid");

        assertThatThrownBy(() -> service.deleteAccount("firebase-uid"))
                .isInstanceOf(AccountDeletionException.class)
                .hasMessageContaining("다시 시도");

        verify(dataDeletionService).deleteByFirebaseUid("firebase-uid");
    }
}
