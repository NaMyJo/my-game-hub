package com.mygamehub.user;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UserAccountControllerTest {

    private final UserAccountDeletionService deletionService =
            mock(UserAccountDeletionService.class);
    private final UserAccountController controller =
            new UserAccountController(deletionService);

    @Test
    void deletesOnlyTheVerifiedRecentlyAuthenticatedUser() {
        MockHttpServletRequest request = request(
                "verified-uid",
                Instant.now().getEpochSecond()
        );
        request.addParameter("uid", "another-user-uid");
        request.addParameter("email", "another-user@example.com");

        var response = controller.deleteAccount(request);

        assertThat(response.getStatusCode().value()).isEqualTo(204);
        verify(deletionService).deleteAccount("verified-uid");
    }

    @Test
    void rejectsMissingAuthentication() {
        assertThatThrownBy(
                () -> controller.deleteAccount(new MockHttpServletRequest())
        ).isInstanceOf(IllegalStateException.class);

        verify(deletionService, never()).deleteAccount("verified-uid");
    }

    @Test
    void rejectsStaleAuthenticationBeforeDeletingAnyData() {
        MockHttpServletRequest request = request(
                "verified-uid",
                Instant.now().minusSeconds(301).getEpochSecond()
        );

        assertThatThrownBy(() -> controller.deleteAccount(request))
                .isInstanceOf(RecentAuthenticationRequiredException.class)
                .hasMessageContaining("다시 로그인");

        verify(deletionService, never()).deleteAccount("verified-uid");
    }

    private MockHttpServletRequest request(String uid, long authTime) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(
                FirebaseAuthInterceptor.USER_ATTRIBUTE,
                new AuthenticatedUser(uid, null, null, null, authTime)
        );
        return request;
    }
}
