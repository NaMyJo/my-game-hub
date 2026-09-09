package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gamefinder.dto.MyGamePickResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/my-game-picks")
public class MyGamePickController {
    private final MyGamePickService service;

    public MyGamePickController(MyGamePickService service) { this.service = service; }

    @GetMapping
    public List<MyGamePickResponse> list(HttpServletRequest request) {
        return service.list(currentUser(request).uid());
    }

    @PostMapping("/{steamAppId}")
    public MyGamePickResponse add(@PathVariable long steamAppId, HttpServletRequest request) {
        return service.add(currentUser(request).uid(), steamAppId);
    }

    @DeleteMapping("/{steamAppId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long steamAppId, HttpServletRequest request) {
        service.remove(currentUser(request).uid(), steamAppId);
    }

    private AuthenticatedUser currentUser(HttpServletRequest request) {
        Object value = request.getAttribute(FirebaseAuthInterceptor.USER_ATTRIBUTE);
        if (!(value instanceof AuthenticatedUser user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        if (user.email() == null || user.email().isBlank()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "로그인하면 게임 저장 기능을 사용할 수 있습니다.");
        }
        return user;
    }
}
