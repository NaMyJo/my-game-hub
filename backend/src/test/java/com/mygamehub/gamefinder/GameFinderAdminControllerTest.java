package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GameFinderAdminControllerTest {
    private final GameFinderAdminAuthorizer authorizer = new GameFinderAdminAuthorizer("admin-uid");
    private final GameFinderAdminMaintenanceService maintenance = mock(GameFinderAdminMaintenanceService.class);
    private final GameFinderAdminController controller =
            new GameFinderAdminController(authorizer, maintenance);

    @Test
    void unauthenticatedRequestIsRejected() {
        assertThatThrownBy(() -> controller.enrich(
                new MockHttpServletRequest(), new GameFinderAdminEnrichRequest(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }

    @Test
    void nonAdminRequestIsRejected() {
        var request = authenticated("regular-user");

        assertThatThrownBy(() -> controller.enrich(request, new GameFinderAdminEnrichRequest(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminRequestPassesRequestedBatchSizeToExistingMaintenancePath() {
        var response = response(1);
        when(maintenance.tryEnrich(1)).thenReturn(Optional.of(response));

        var actual = controller.enrich(authenticated("admin-uid"),
                new GameFinderAdminEnrichRequest(1));

        assertThat(actual).isSameAs(response);
        verify(maintenance).tryEnrich(1);
    }

    @Test
    void concurrentRequestIsReportedAsConflict() {
        when(maintenance.tryEnrich(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.enrich(
                authenticated("admin-uid"), new GameFinderAdminEnrichRequest(1)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    private MockHttpServletRequest authenticated(String uid) {
        var request = new MockHttpServletRequest();
        request.setAttribute(FirebaseAuthInterceptor.USER_ATTRIBUTE,
                new AuthenticatedUser(uid, null, null, null));
        return request;
    }

    private GameFinderAdminEnrichResponse response(int batchSize) {
        return new GameFinderAdminEnrichResponse(
                batchSize, 1, 1, 0, 0, 0, 1, 0, 0, 0, 10);
    }
}
