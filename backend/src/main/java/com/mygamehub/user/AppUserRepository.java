package com.mygamehub.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, String> {
    Optional<AppUser> findByPublicIdAndProfilePublicTrue(String publicId);
}
