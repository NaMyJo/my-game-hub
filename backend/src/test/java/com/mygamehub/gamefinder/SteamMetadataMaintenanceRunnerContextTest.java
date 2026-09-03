package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.TaskScheduler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SteamMetadataMaintenanceRunnerContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void createsRunnerUsingProductionConstructorAndApplicationDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SteamMetadataMaintenanceRunner.class);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import(SteamMetadataMaintenanceRunner.class)
    static class TestConfiguration {
        @Bean MaintenanceJobStateService states() {
            return mock(MaintenanceJobStateService.class);
        }

        @Bean SteamCatalogSyncService sync() {
            return mock(SteamCatalogSyncService.class);
        }

        @Bean GameFinderAdminMaintenanceService maintenance() {
            return mock(GameFinderAdminMaintenanceService.class);
        }

        @Bean(name = "gameFinderMaintenanceTaskScheduler")
        TaskScheduler scheduler() {
            return mock(TaskScheduler.class);
        }
    }
}
