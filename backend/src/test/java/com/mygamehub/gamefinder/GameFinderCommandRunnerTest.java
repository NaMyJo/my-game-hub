package com.mygamehub.gamefinder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.ConfigurableApplicationContext;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class GameFinderCommandRunnerTest {
    @Test
    void enrichRunsAndClosesApplicationContext() {
        var sync = mock(SteamCatalogSyncService.class);
        var smoke = mock(GameFinderSmokeService.class);
        var verifier = mock(SteamMetadataVerificationService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, verifier, context,
                "enrich", "570", 100, "RANDOM");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(sync).enrichBatch();
        verify(context).close();
    }

    @Test
    void metadataVerifyUsesConfiguredSampleAndModeAndClosesContext() {
        var sync = mock(SteamCatalogSyncService.class);
        var smoke = mock(GameFinderSmokeService.class);
        var verifier = mock(SteamMetadataVerificationService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, verifier, context,
                "metadata-verify", "570", 25, "RECENT");

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(verifier).verify(25,
                SteamMetadataVerificationService.VerificationMode.RECENT);
        verify(context).close();
    }
}
