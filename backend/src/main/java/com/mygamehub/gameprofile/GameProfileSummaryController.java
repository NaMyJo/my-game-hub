package com.mygamehub.gameprofile;

import com.mygamehub.gameprofile.dto.GameProfileSummaryRequest;
import com.mygamehub.gameprofile.dto.GameProfileSummaryResponse;

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
            @RequestHeader("X-User-Uid") String userUid
    ) {
        return service
                .get(userUid)
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
            @RequestHeader("X-User-Uid") String userUid,
            @RequestBody GameProfileSummaryRequest request
    ) {
        GameProfileSummaryResponse response =
                service.save(
                        userUid,
                        request
                );

        return ResponseEntity.ok(response);
    }
}