package com.mygamehub.gameidentity;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.gameidentity.dto.GameIdentityHistoryRequest;
import com.mygamehub.gameidentity.dto.GameIdentityHistoryResponse;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/game-identities/latest")
public class GameIdentityHistoryController {

    private final GameIdentityHistoryService service;

    public GameIdentityHistoryController(
            GameIdentityHistoryService service
    ) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<GameIdentityHistoryResponse> getLatest(
            HttpServletRequest request
    ) {
        AuthenticatedUser user =
                currentUser(request);

        return service
                .getLatest(user.uid())
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity
                                .notFound()
                                .build()
                );
    }

    @PutMapping
    public ResponseEntity<GameIdentityHistoryResponse> saveLatest(
            HttpServletRequest request,
            @RequestBody GameIdentityHistoryRequest body
    ) {
        AuthenticatedUser user =
                currentUser(request);

        GameIdentityHistoryResponse response =
                service.save(
                        user.uid(),
                        body
                );

        return ResponseEntity.ok(response);
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
}