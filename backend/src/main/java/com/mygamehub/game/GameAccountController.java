package com.mygamehub.game;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.game.dto.GameAccountResponse;
import com.mygamehub.game.dto.RegisterGameRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import com.mygamehub.game.dto.GameAccountReorderRequest;
import java.util.List;

@RestController
@RequestMapping("/api/me/games")
public class GameAccountController {

    private final GameAccountService service;

    public GameAccountController(GameAccountService service) {
        this.service = service;
    }

    @GetMapping
    public List<GameAccountResponse> list(HttpServletRequest request) {
        AuthenticatedUser user = currentUser(request);
        return service.list(user.uid());
    }

    @PostMapping
    public GameAccountResponse register(
            HttpServletRequest request,
            @Valid @RequestBody RegisterGameRequest body
    ) {
        return service.register(currentUser(request), body);
    }

    @PostMapping("/{id}/refresh")
    public GameAccountResponse refresh(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        return service.refresh(currentUser(request), id);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            HttpServletRequest request,
            @PathVariable Long id
    ) {
        service.delete(currentUser(request).uid(), id);
    }
    @PutMapping("/reorder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reorder(
            HttpServletRequest request,
            @RequestBody GameAccountReorderRequest body
    ) {
        System.out.println("===== REORDER CONTROLLER HIT =====");
        System.out.println("gameIds = " + body.gameIds());

        service.reorderGames(
                currentUser(request).uid(),
                body.gameIds()
        );
    }
    private AuthenticatedUser currentUser(HttpServletRequest request) {
        return (AuthenticatedUser) request.getAttribute(
                FirebaseAuthInterceptor.USER_ATTRIBUTE
        );
    }
}
