package com.mygamehub.gameidentity;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.gameidentity.dto.CreateGameIdentityRequest;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewRequest;
import com.mygamehub.gameidentity.dto.GameIdentityPreviewResponse;
import com.mygamehub.gameidentity.dto.GameIdentityResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/me/game-identities")
public class GameIdentityController {

    private final GameIdentityService service;
    private final GameIdentityPreviewService previewService;
    public GameIdentityController(
            GameIdentityService service,
            GameIdentityPreviewService previewService
    ) {
        this.service = service;
        this.previewService = previewService;
    }

    @PostMapping
    public GameIdentityResponse create(
            HttpServletRequest request,
            @Valid @RequestBody
            CreateGameIdentityRequest body
    ) {
        AuthenticatedUser user =
                currentUser(request);

        return service.create(
                user.uid(),
                body
        );
    }

    @GetMapping
    public List<GameIdentityResponse> list(
            HttpServletRequest request
    ) {
        return service.list(
                currentUser(request).uid()
        );
    }

    @GetMapping("/{id}")
    public GameIdentityResponse get(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        return service.get(
                currentUser(request).uid(),
                id
        );
    }

    @DeleteMapping("/{id}")
    public void delete(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        service.delete(
                currentUser(request).uid(),
                id
        );
    }

    private AuthenticatedUser currentUser(
            HttpServletRequest request
    ) {
        Object value =
                request.getAttribute(
                        "authenticatedUser"
                );

        if (value instanceof AuthenticatedUser user) {
            return user;
        }

        throw new IllegalStateException(
                "인증된 사용자 정보가 없습니다."
        );
    }
    @PostMapping("/preview")
    public GameIdentityPreviewResponse preview(
            HttpServletRequest request,
            @Valid @RequestBody
            GameIdentityPreviewRequest body
    ) {
        return previewService.preview(
                currentUser(request).uid(),
                body
        );
    }
}