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
        String safeUid = safeUid(firebaseUid);
        log.info("Account deletion started: uid={}", safeUid);

        try {
            userDataDeletionService.deleteByFirebaseUid(firebaseUid);
            log.info("Account data deletion completed: uid={}", safeUid);
        } catch (RuntimeException e) {
            log.error(
                    "Account data deletion failed: uid={}, stage=database",
                    safeUid
            );
            throw new AccountDeletionException(
                    "계정 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    e
            );
        }

        try {
            firebaseUserDeletionService.deleteUser(firebaseUid);
            log.info("Firebase account deletion completed: uid={}", safeUid);
        } catch (FirebaseAuthException | IllegalStateException e) {
            log.error("Firebase account deletion failed: uid={}, stage=firebase", safeUid);
            throw new AccountDeletionException(
                    "계정 삭제에 실패했습니다. 잠시 후 다시 시도해주세요.",
                    e
            );
        }
    }

    private String safeUid(String firebaseUid) {
        if (firebaseUid == null || firebaseUid.isBlank()) {
            return "unknown";
        }
        return firebaseUid.substring(0, Math.min(8, firebaseUid.length()));
    }
}
