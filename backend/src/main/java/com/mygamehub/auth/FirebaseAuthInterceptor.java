package com.mygamehub.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class FirebaseAuthInterceptor implements HandlerInterceptor {

    public static final String USER_ATTRIBUTE = "authenticatedUser";

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");

        if (authorization == null || !authorization.startsWith("Bearer ")) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing Firebase ID token");
            return false;
        }

        String idToken = authorization.substring("Bearer ".length()).trim();

        try {
            FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(idToken);

            AuthenticatedUser user = new AuthenticatedUser(
                    token.getUid(),
                    token.getEmail(),
                    token.getName(),
                    token.getPicture()
            );

            request.setAttribute(USER_ATTRIBUTE, user);
            return true;
        } catch (FirebaseAuthException e) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid Firebase ID token");
            return false;
        }
    }
}
