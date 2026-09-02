package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminMeResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminStatusResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminCatalogExpandRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminCatalogExpandResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminFullCatalogSyncResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminGameCatalogSyncResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/game-finder")
public class GameFinderAdminController {
    private final GameFinderAdminAuthorizer authorizer;
    private final GameFinderAdminMaintenanceService maintenance;
    private final GameFinderAdminStatusService statusService;

    public GameFinderAdminController(
            GameFinderAdminAuthorizer authorizer,
            GameFinderAdminMaintenanceService maintenance,
            GameFinderAdminStatusService statusService) {
        this.authorizer = authorizer;
        this.maintenance = maintenance;
        this.statusService = statusService;
    }

    @GetMapping("/me")
    public GameFinderAdminMeResponse me(HttpServletRequest request) {
        return new GameFinderAdminMeResponse(authorizer.isAdmin(currentUser(request).uid()));
    }

    @GetMapping("/status")
    public GameFinderAdminStatusResponse status(HttpServletRequest request) {
        requireAdmin(request);
        return statusService.status();
    }

    @PostMapping("/enrich")
    public GameFinderAdminEnrichResponse enrich(
            HttpServletRequest servletRequest,
            @Valid @RequestBody GameFinderAdminEnrichRequest request) {
        requireAdmin(servletRequest);
        return maintenance.tryEnrich(request.effectiveBatchSize())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "GAME FINDER enrichment is already running"));
    }

    @PostMapping("/catalog/expand")
    public GameFinderAdminCatalogExpandResponse expandCatalog(
            HttpServletRequest servletRequest,
            @Valid @RequestBody GameFinderAdminCatalogExpandRequest request) {
        requireAdmin(servletRequest);
        if (!request.supportedTarget()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "targetTotal must be one of 500, 1000, 5000, 10000");
        }
        return maintenance.tryExpandCatalog(request.targetTotal())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "GAME FINDER maintenance is already running"));
    }

    @PostMapping("/catalog/full-sync")
    public GameFinderAdminFullCatalogSyncResponse fullCatalogSync(
            HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return maintenance.tryFullCatalogSync()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "GAME FINDER maintenance is already running"));
    }

    @PostMapping("/catalog/game-only-sync")
    public GameFinderAdminGameCatalogSyncResponse gameCatalogSync(
            HttpServletRequest servletRequest) {
        requireAdmin(servletRequest);
        return maintenance.tryGameCatalogSync()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "GAME FINDER maintenance is already running"));
    }

    private AuthenticatedUser currentUser(HttpServletRequest servletRequest) {
        Object value = servletRequest.getAttribute(FirebaseAuthInterceptor.USER_ATTRIBUTE);
        if (!(value instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        return user;
    }

    private void requireAdmin(HttpServletRequest request) {
        AuthenticatedUser user = currentUser(request);
        if (!authorizer.isAdmin(user.uid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }
}
