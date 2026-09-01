package com.mygamehub.publicprofile;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewResponse;
import com.mygamehub.publicprofile.dto.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PublicProfileController {
    private final PublicProfileService service;

    public PublicProfileController(PublicProfileService service) {
        this.service = service;
    }

    @GetMapping("/me/game-power-analysis")
    public GameIdentityPreviewResponse analysis(HttpServletRequest request) {
        return service.analysis(currentUser(request));
    }

    @GetMapping("/me/public-profile")
    public PublicProfileSettingsResponse settings(HttpServletRequest request) {
        return service.settings(currentUser(request));
    }

    @PutMapping("/me/public-profile")
    public PublicProfileSettingsResponse updateSettings(
            HttpServletRequest request,
            @RequestBody PublicProfileSettingsRequest body
    ) {
        return service.updateSettings(currentUser(request), body.isPublic());
    }

    @GetMapping("/public/profiles/{publicId}")
    public ResponseEntity<PublicProfileResponse> publicProfile(
            @PathVariable String publicId
    ) {
        return service.findPublic(publicId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/me/game-identities/share")
    public ResponseEntity<IdentityShareResponse> shareSettings(
            HttpServletRequest request
    ) {
        return service.shareSettings(currentUser(request).uid())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/me/game-identities/share")
    public IdentityShareResponse enableShare(HttpServletRequest request) {
        return service.enableShare(currentUser(request).uid());
    }

    @DeleteMapping("/me/game-identities/share")
    public IdentityShareResponse disableShare(HttpServletRequest request) {
        return service.disableShare(currentUser(request).uid());
    }

    @GetMapping("/public/identities/{shareId}")
    public ResponseEntity<PublicIdentityResponse> sharedIdentity(
            @PathVariable String shareId
    ) {
        return service.findSharedIdentity(shareId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(FirebaseAuthInterceptor.USER_ATTRIBUTE);
        if (value instanceof AuthenticatedUser user) return user;
        throw new IllegalStateException("인증된 사용자 정보가 없습니다.");
    }
}
