package com.mygamehub.gamefinder;

import com.mygamehub.gamefinder.dto.GameFinderAdminEnrichRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GameFinderAdminEnrichRequestTest {
    @Test
    void defaultsToOneAndAcceptsSafeRangeOnly() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(new GameFinderAdminEnrichRequest(null).effectiveBatchSize()).isEqualTo(1);
            assertThat(validator.validate(new GameFinderAdminEnrichRequest(1))).isEmpty();
            assertThat(validator.validate(new GameFinderAdminEnrichRequest(40))).isEmpty();
            assertThat(validator.validate(new GameFinderAdminEnrichRequest(0))).isNotEmpty();
            assertThat(validator.validate(new GameFinderAdminEnrichRequest(41))).isNotEmpty();
        }
    }
}
