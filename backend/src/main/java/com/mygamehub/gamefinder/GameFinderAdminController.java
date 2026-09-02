package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/game-finder")
public class GameFinderAdminController {
    private final GameFinderAdminAuthorizer authorizer;
    private final GameFinderAdminMaintenanceService maintenance;

    public GameFinderAdminController(
            GameFinderAdminAuthorizer authorizer,
            GameFinderAdminMaintenanceService maintenance) {
        this.authorizer = authorizer;
        this.maintenance = maintenance;
    }

    @PostMapping("/enrich")
    public GameFinderAdminEnrichResponse enrich(
            HttpServletRequest servletRequest,
            @Valid @RequestBody GameFinderAdminEnrichRequest request) {
        Object value = servletRequest.getAttribute(FirebaseAuthInterceptor.USER_ATTRIBUTE);
        if (!(value instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }
        if (!authorizer.isAdmin(user.uid())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        return maintenance.tryEnrich(request.effectiveBatchSize())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "GAME FINDER enrichment is already running"));
    }
}
