package com.mygamehub.gamefinder;

import com.mygamehub.auth.AuthenticatedUser;
import com.mygamehub.auth.FirebaseAuthInterceptor;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminStatusResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminCatalogExpandRequest;
import com.mygamehub.gamefinder.dto.GameFinderAdminCatalogExpandResponse;
import com.mygamehub.gamefinder.dto.GameFinderAdminFullCatalogSyncResponse;
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
    private final GameFinderAdminStatusService statusService = mock(GameFinderAdminStatusService.class);
    private final GameFinderAdminController controller =
            new GameFinderAdminController(authorizer, maintenance, statusService);

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

    @Test
    void meReturnsFalseWithoutExposingAllowlist() {
        assertThat(controller.me(authenticated("regular-user")).admin()).isFalse();
        assertThat(controller.me(authenticated("admin-uid")).admin()).isTrue();
    }

    @Test
    void statusRequiresAuthenticationAndAdminAuthorization() {
        assertThatThrownBy(() -> controller.status(new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> controller.status(authenticated("regular-user")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void adminCanReadAggregateStatus() {
        var counts = new GameFinderAdminStatusResponse.EnrichmentCounts(1, 2, 3, 4, 5);
        var checkpoint = new GameFinderAdminStatusResponse.Checkpoint(10L, null, "SUCCESS", false);
        var fullSync = new GameFinderAdminStatusResponse.FullCatalogSync(
                "NEW", 0L, 0, null, false, false);
        var status = new GameFinderAdminStatusResponse(
                15, 13, 1, 1, counts, counts, checkpoint, fullSync);
        when(statusService.status()).thenReturn(status);

        assertThat(controller.status(authenticated("admin-uid"))).isSameAs(status);
    }

    @Test
    void catalogExpandRequiresAuthenticationAndAdminAuthorization() {
        var body = new GameFinderAdminCatalogExpandRequest(500);
        assertThatThrownBy(() -> controller.expandCatalog(new MockHttpServletRequest(), body))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> controller.expandCatalog(authenticated("regular-user"), body))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void catalogExpandValidatesTargetAndDelegatesForAdmin() {
        assertThatThrownBy(() -> controller.expandCatalog(authenticated("admin-uid"),
                new GameFinderAdminCatalogExpandRequest(750)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        var response = new GameFinderAdminCatalogExpandResponse(
                400, 400, 398, 500, 500, true, 10);
        when(maintenance.tryExpandCatalog(500)).thenReturn(Optional.of(response));

        assertThat(controller.expandCatalog(authenticated("admin-uid"),
                new GameFinderAdminCatalogExpandRequest(500))).isSameAs(response);
        verify(maintenance).tryExpandCatalog(500);
    }

    @Test
    void fullCatalogSyncRequiresAdminAndUsesMaintenancePath() {
        assertThatThrownBy(() -> controller.fullCatalogSync(new MockHttpServletRequest()))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> controller.fullCatalogSync(authenticated("regular-user")))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
        var response = new GameFinderAdminFullCatalogSyncResponse(
                500, 498, 10500, 12345, 10000, false, 100);
        when(maintenance.tryFullCatalogSync()).thenReturn(Optional.of(response));

        assertThat(controller.fullCatalogSync(authenticated("admin-uid"))).isSameAs(response);
        verify(maintenance).tryFullCatalogSync();
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
