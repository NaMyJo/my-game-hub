package com.mygamehub.gameprofile;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.gameprofile.dto.GameProfileSummaryRequest;
import com.mygamehub.gameprofile.dto.GameProfileSummaryResponse;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/game-profile")
public class GameProfileSummaryController {

    private final GameProfileSummaryService service;

    public GameProfileSummaryController(
            GameProfileSummaryService service
    ) {
        this.service = service;
    }

    // ==============================
    // 게임 프로필 조회
    // ==============================

    @GetMapping
    public ResponseEntity<GameProfileSummaryResponse> getProfile(
            HttpServletRequest request
    ) {
        AuthenticatedUser user =
                currentUser(request);

        return service
                .get(user.uid())
                .map(ResponseEntity::ok)
                .orElseGet(
                        () -> ResponseEntity
                                .notFound()
                                .build()
                );
    }

    // ==============================
    // 게임 프로필 저장 / 수정
    // ==============================

    @PutMapping
    public ResponseEntity<GameProfileSummaryResponse> saveProfile(
            HttpServletRequest request,
            @RequestBody GameProfileSummaryRequest body
    ) {
        AuthenticatedUser user =
                currentUser(request);

        GameProfileSummaryResponse response =
                service.save(
                        user.uid(),
                        body
                );

        return ResponseEntity.ok(response);
    }

    // ==============================
    // 현재 로그인 사용자
    // ==============================

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