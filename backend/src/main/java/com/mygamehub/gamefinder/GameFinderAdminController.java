package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminMeResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminStatusResponse;
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
