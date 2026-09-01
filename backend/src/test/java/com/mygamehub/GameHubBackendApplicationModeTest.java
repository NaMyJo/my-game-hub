package com.mygamehub;

import com.google.firebase.FirebaseApp;
import com.mygamehub.config.FirebaseConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GameHubBackendApplicationModeTest {
    @Test
    void commandArgumentForcesNonWebLazyApplication() {
        SpringApplication application = GameHubBackendApplication.createApplication(
                new String[]{"--app.game-finder.command=enrich"}, Map.of());

        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
    }

    @Test
    void commandEnvironmentAlsoForcesNonWebApplication() {
        SpringApplication application = GameHubBackendApplication.createApplication(
                new String[0], Map.of("GAME_FINDER_COMMAND", "taxonomy"));

        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.NONE);
    }

    @Test
    void commandModeContextDoesNotStartEmbeddedWebServer() {
        String[] args = {"--app.game-finder.command=enrich"};
        SpringApplication application = new SpringApplication(BatchSizeProbe.class);
        GameHubBackendApplication.configureApplication(application, args, Map.of());

        try (var context = application.run(args)) {
            assertThat(context).isNotInstanceOf(WebServerApplicationContext.class);
        }
    }

    @Test
    void ordinaryStartupRemainsServletWebApplication() {
        SpringApplication application = GameHubBackendApplication.createApplication(
                new String[0], Map.of());

        assertThat(application.getWebApplicationType()).isEqualTo(WebApplicationType.SERVLET);
    }

    @Test
    void commandLineBatchSizeOverridesEnvironmentStyleDefault() {
        SpringApplication application = new SpringApplication(BatchSizeProbe.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("app.game-finder.sync-batch-size", "40"));
        try (var context = application.run("--app.game-finder.sync-batch-size=1")) {
            assertThat(context.getBean(BatchSizeProbeValue.class).value()).isEqualTo(1);
        }
    }

    @Test
    void firebaseConfigurationIsAbsentInCommandMode() {
        new ApplicationContextRunner()
                .withUserConfiguration(FirebaseConfig.class)
                .withPropertyValues("app.game-finder.command=enrich")
                .run(context -> assertThat(context.getBeansOfType(FirebaseApp.class)).isEmpty());
    }

    @Configuration(proxyBeanMethods = false)
    static class BatchSizeProbe {
        @Bean
        BatchSizeProbeValue batchSizeProbeValue(
                @Value("${app.game-finder.sync-batch-size:40}") int value) {
            return new BatchSizeProbeValue(value);
        }
    }

    record BatchSizeProbeValue(int value) {}
}
