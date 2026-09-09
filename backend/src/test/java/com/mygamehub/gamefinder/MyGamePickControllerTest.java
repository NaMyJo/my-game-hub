package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MyGamePickControllerTest {
    private final MyGamePickService service = mock(MyGamePickService.class);
    private final MyGamePickController controller = new MyGamePickController(service);

    @Test
    void rejectsMissingAndAnonymousAuthentication() {
        assertThatThrownBy(() -> controller.list(new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        var anonymous = request("anonymous-uid", null);
        assertThatThrownBy(() -> controller.list(anonymous))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void delegatesUsingVerifiedFirebaseUid() {
        var request = request("firebase-uid", "user@example.com");

        controller.list(request);
        controller.add(570, request);
        controller.remove(570, request);

        verify(service).list("firebase-uid");
        verify(service).add("firebase-uid", 570);
        verify(service).remove("firebase-uid", 570);
    }

    private MockHttpServletRequest request(String uid, String email) {
        var request = new MockHttpServletRequest();
        request.setAttribute(FirebaseAuthInterceptor.USER_ATTRIBUTE,
                new AuthenticatedUser(uid, email, null, null));
        return request;
    }
}
