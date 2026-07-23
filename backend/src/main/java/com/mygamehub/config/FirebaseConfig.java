package com.mygamehub.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Configuration
public class FirebaseConfig {

    @Bean
    FirebaseApp firebaseApp() throws IOException {
        if (!FirebaseApp.getApps().isEmpty()) {
            return FirebaseApp.getInstance();
        }

        String firebaseServiceAccountJson =
                System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");

        GoogleCredentials credentials;

        if (firebaseServiceAccountJson != null
                && !firebaseServiceAccountJson.isBlank()) {

            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(
                            firebaseServiceAccountJson.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    )
            );

        } else {
            // 로컬 개발에서는 기존 방식 사용 가능
            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        return FirebaseApp.initializeApp(options);
    }
}