package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SteamStoreRequestPolicyContextTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(TestConfiguration.class);

    @Test
    void createsPolicyAndSyncServiceFromApplicationDefaultsWithoutEnvironmentVariables() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(SteamStoreRequestPolicy.class);

            SteamStoreRequestPolicy policy = context.getBean(SteamStoreRequestPolicy.class);
            assertThat(policy.requestDelayMs()).isEqualTo(500);
            assertThat(policy.maxRetries()).isEqualTo(2);
            assertThat(policy.initialMaxRetries()).isZero();
            assertThat(policy.initialBackoffMs()).isEqualTo(500);
            assertThat(policy.maxBackoffMs()).isEqualTo(10_000);

            SteamCatalogSyncService sync = context.getBean(SteamCatalogSyncService.class);
            assertThat(sync.metadataConcurrency()).isEqualTo(1);
            assertThat(sync.storeRequestDelayMs()).isEqualTo(500);
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({SteamStoreRequestPolicy.class, SteamCatalogSyncService.class})
    static class TestConfiguration {
        @Bean SteamCatalogClient catalog() { return mock(SteamCatalogClient.class); }
        @Bean SteamStoreDetailClient store() { return mock(SteamStoreDetailClient.class); }
        @Bean SteamGameRepository games() { return mock(SteamGameRepository.class); }
        @Bean SteamCatalogPersistenceService persistence() {
            return mock(SteamCatalogPersistenceService.class);
        }
        @Bean CatalogSyncCheckpointRepository checkpoints() {
            return mock(CatalogSyncCheckpointRepository.class);
        }
        @Bean IgdbEnrichmentClient igdb() { return mock(IgdbEnrichmentClient.class); }
        @Bean GameTagService tags() { return mock(GameTagService.class); }
    }
}
