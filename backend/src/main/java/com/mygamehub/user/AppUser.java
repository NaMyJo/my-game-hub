package com.mygamehub.user;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "app_users")
public class AppUser {

    @Id
    @Column(length = 128)
    private String firebaseUid;

    @Column
    private String email;

    @Column
    private String displayName;

    @Column(length = 1024)
    private String photoUrl;

    @Column(nullable = false)
    private Instant updatedAt;

    protected AppUser() {
    }

    public AppUser(
            String firebaseUid,
            String email,
            String displayName,
            String photoUrl
    ) {
        this.firebaseUid = firebaseUid;
        this.email = email;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
        this.updatedAt = Instant.now();
    }

    public void sync(String email, String displayName, String photoUrl) {
        this.email = email;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
        this.updatedAt = Instant.now();
    }

    public String getFirebaseUid() {
        return firebaseUid;
    }

    public String getEmail() {
        return email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }
}
