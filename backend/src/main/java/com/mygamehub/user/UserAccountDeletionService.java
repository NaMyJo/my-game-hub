package com.mygamehub.user;

import com.google.firebase.auth.FirebaseAuthException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class UserAccountDeletionService {

    private static final Logger log =
            LoggerFactory.getLogger(UserAccountDeletionService.class);

    private final UserDataDeletionService userDataDeletionService;
    private final FirebaseUserDeletionService firebaseUserDeletionService;

    public UserAccountDeletionService(
            UserDataDeletionService userDataDeletionService,
            FirebaseUserDeletionService firebaseUserDeletionService
    ) {
        this.userDataDeletionService = userDataDeletionService;
        this.firebaseUserDeletionService = firebaseUserDeletionService;
    }

    public void deleteAccount(String firebaseUid) {
        log.info("Account deletion started");

        try {
            userDataDeletionService.deleteByFirebaseUid(firebaseUid);
            log.info("Account data deletion completed");
        } catch (RuntimeException e) {
            log.error(
                    "Account data deletion failed: stage=database"
            );
            throw new AccountDeletionException(
                    "계정 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    e
            );
        }

        try {
            firebaseUserDeletionService.deleteUser(firebaseUid);
            log.info("Firebase account deletion completed");
        } catch (FirebaseAuthException | IllegalStateException e) {
            log.error("Firebase account deletion failed: stage=firebase");
            throw new AccountDeletionException(
                    "계정 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    e
            );
        }
    }

}
