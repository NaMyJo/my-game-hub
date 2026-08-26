package com.mygamehub.user;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.user.dto.UserProfileRequest;
import com.mygamehub.user.dto.UserProfileResponse;
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

    @Transactional
    public UserProfileResponse getProfile(AuthenticatedUser authUser) {
        return UserProfileResponse.from(sync(authUser));
    }

    @Transactional
    public UserProfileResponse updateProfile(
            AuthenticatedUser authUser,
            UserProfileRequest request
    ) {
        String nickname = request.nickname() == null
                ? ""
                : request.nickname().trim();
        String introduction = request.introduction() == null
                ? ""
                : request.introduction().trim();

        if (nickname.isEmpty()) {
            throw new IllegalArgumentException("닉네임을 입력해 주세요.");
        }

        if (nickname.length() > 50 || introduction.length() > 120) {
            throw new IllegalArgumentException("프로필 입력 길이를 확인해 주세요.");
        }

        AppUser user = sync(authUser);
        user.updateProfile(nickname, introduction);
        return UserProfileResponse.from(repository.save(user));
    }
}
