package com.mygamehub.user;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.user.dto.UserProfileRequest;
import com.mygamehub.user.dto.UserProfileResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me/profile")
public class UserProfileController {

    private final UserService userService;

    public UserProfileController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<UserProfileResponse> getProfile(
            HttpServletRequest request
    ) {
        return ResponseEntity.ok(
                userService.getProfile(currentUser(request))
        );
    }

    @PutMapping
    public ResponseEntity<UserProfileResponse> updateProfile(
            HttpServletRequest request,
            @RequestBody UserProfileRequest body
    ) {
        return ResponseEntity.ok(
                userService.updateProfile(currentUser(request), body)
        );
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
