package com.mygamehub.user;

import com.mygamehub.auth.AuthenticatedUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final AppUserRepository repository;

    public UserService(AppUserRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AppUser sync(AuthenticatedUser authUser) {
        AppUser user = repository.findById(authUser.uid())
                .orElseGet(() -> new AppUser(
                        authUser.uid(),
                        authUser.email(),
                        authUser.name(),
                        authUser.picture()
                ));

        user.sync(authUser.email(), authUser.name(), authUser.picture());
        return repository.save(user);
    }
}
