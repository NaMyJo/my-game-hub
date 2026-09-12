package com.mygamehub.user;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/me/account")
public class UserAccountController {

    private static final long RECENT_AUTH_MAX_AGE_SECONDS = 300;

    private final UserAccountDeletionService deletionService;

    public UserAccountController(UserAccountDeletionService deletionService) {
        this.deletionService = deletionService;
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(HttpServletRequest request) {
        AuthenticatedUser user = currentUser(request);
        requireRecentAuthentication(user);
        deletionService.deleteAccount(user.uid());
        return ResponseEntity.noContent().build();
    }

    private void requireRecentAuthentication(AuthenticatedUser user) {
        Long authTime = user.authTimeEpochSeconds();
        long oldestAllowed = Instant.now().getEpochSecond()
                - RECENT_AUTH_MAX_AGE_SECONDS;
        if (authTime == null || authTime < oldestAllowed) {
            throw new RecentAuthenticationRequiredException();
        }
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(
                FirebaseAuthInterceptor.USER_ATTRIBUTE
        );
        if (value instanceof AuthenticatedUser user) {
            return user;
        }
        throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
    }
}
