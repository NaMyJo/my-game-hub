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
        var taxonomy = mock(GameTaxonomyRebuildService.class);
        var diagnostic = mock(IgdbTaxonomyDiagnosticService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, verifier, taxonomy, diagnostic, context,
                "enrich", "570", 100, "RANDOM", 200);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(sync).enrichBatch();
        verify(context).close();
    }

    @Test
    void metadataVerifyUsesConfiguredSampleAndModeAndClosesContext() {
        var sync = mock(SteamCatalogSyncService.class);
        var smoke = mock(GameFinderSmokeService.class);
        var verifier = mock(SteamMetadataVerificationService.class);
        var taxonomy = mock(GameTaxonomyRebuildService.class);
        var diagnostic = mock(IgdbTaxonomyDiagnosticService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, verifier, taxonomy, diagnostic, context,
                "metadata-verify", "570", 25, "RECENT", 200);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(verifier).verify(25,
                SteamMetadataVerificationService.VerificationMode.RECENT);
        verify(context).close();
    }

    @Test
    void taxonomyUsesVersionedRebuildService() {
        var sync = mock(SteamCatalogSyncService.class);
        var smoke = mock(GameFinderSmokeService.class);
        var verifier = mock(SteamMetadataVerificationService.class);
        var taxonomy = mock(GameTaxonomyRebuildService.class);
        var diagnostic = mock(IgdbTaxonomyDiagnosticService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, verifier, taxonomy, diagnostic, context,
                "taxonomy", "570", 100, "RANDOM", 300);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(taxonomy).rebuildBatch(300);
        verify(context).close();
    }

    @Test
    void igdbTaxonomyDiagnosticUsesReadOnlyService() {
        var sync = mock(SteamCatalogSyncService.class);
        var smoke = mock(GameFinderSmokeService.class);
        var verifier = mock(SteamMetadataVerificationService.class);
        var taxonomy = mock(GameTaxonomyRebuildService.class);
        var diagnostic = mock(IgdbTaxonomyDiagnosticService.class);
        var context = mock(ConfigurableApplicationContext.class);
        var runner = new GameFinderCommandRunner(sync, smoke, verifier, taxonomy, diagnostic, context,
                "igdb-taxonomy-diagnostic", "570", 100, "RANDOM", 200);

        runner.run(new DefaultApplicationArguments(new String[0]));

        verify(diagnostic).run();
        verify(context).close();
    }
}
