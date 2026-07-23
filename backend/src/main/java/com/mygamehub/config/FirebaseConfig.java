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

        String serviceAccountJson =
                System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");

        GoogleCredentials credentials;

        if (serviceAccountJson != null && !serviceAccountJson.isBlank()) {

            System.out.println(
                    "Firebase credentials: FIREBASE_SERVICE_ACCOUNT_JSON"
            );

            credentials = GoogleCredentials.fromStream(
                    new ByteArrayInputStream(
                            serviceAccountJson.getBytes(StandardCharsets.UTF_8)
                    )
            );

        } else {

            System.out.println(
                    "Firebase credentials: Application Default Credentials"
            );

            credentials = GoogleCredentials.getApplicationDefault();
        }

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .build();

        return FirebaseApp.initializeApp(options);
    }
}