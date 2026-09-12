package com.mygamehub.user;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
public class FirebaseUserDeletionService {

    private final ObjectProvider<FirebaseApp> firebaseAppProvider;

    public FirebaseUserDeletionService(
            ObjectProvider<FirebaseApp> firebaseAppProvider
    ) {
        this.firebaseAppProvider = firebaseAppProvider;
    }

    public void deleteUser(String firebaseUid) throws FirebaseAuthException {
        FirebaseApp firebaseApp = firebaseAppProvider.getIfAvailable();
        if (firebaseApp == null) {
            throw new IllegalStateException("Firebase Admin이 초기화되지 않았습니다.");
        }

        try {
            FirebaseAuth.getInstance(firebaseApp).deleteUser(firebaseUid);
        } catch (FirebaseAuthException e) {
            if (e.getAuthErrorCode() != AuthErrorCode.USER_NOT_FOUND) {
                throw e;
            }
        }
    }
}
